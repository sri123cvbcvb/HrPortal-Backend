package com.hrms.backend.service;

import com.hrms.backend.dto.AttendanceSummaryDto;
import com.hrms.backend.dto.MonthlyAttendanceDto;
import com.hrms.backend.model.AttendanceRecord;
import com.hrms.backend.model.CompanyLeave;
import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.AttendanceRepository;
import com.hrms.backend.repository.CompanyLeaveRepository;
import com.hrms.backend.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final CompanyLeaveRepository companyLeaveRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public AttendanceService(AttendanceRepository attendanceRepository, CompanyLeaveRepository companyLeaveRepository,
            LeaveRequestRepository leaveRequestRepository) {
        this.attendanceRepository = attendanceRepository;
        this.companyLeaveRepository = companyLeaveRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public AttendanceSummaryDto getMonthlySummary(User user, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        List<CompanyLeave> companyLeaves = companyLeaveRepository.findByDateBetween(startOfMonth, endOfMonth);
        Set<LocalDate> companyLeaveDates = companyLeaves.stream()
                .map(CompanyLeave::getDate)
                .collect(Collectors.toSet());

        List<AttendanceRecord> records = attendanceRepository.findByUserAndDateBetween(user, startOfMonth, endOfMonth);
        Set<LocalDate> presentDates = records.stream()
                .filter(r -> "Present".equalsIgnoreCase(r.getStatus()))
                .map(AttendanceRecord::getDate)
                .collect(Collectors.toSet());

        // Build a set of dates covered by approved employee leave requests
        Set<LocalDate> onLeaveDates = new HashSet<>();
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesInRange(user, startOfMonth,
                endOfMonth);
        for (LeaveRequest lr : approvedLeaves) {
            LocalDate d = lr.getFromDate();
            while (!d.isAfter(lr.getToDate())) {
                if (!d.isBefore(startOfMonth) && !d.isAfter(endOfMonth)) {
                    onLeaveDates.add(d);
                }
                d = d.plusDays(1);
            }
        }

        int totalWorkingDays = 0;
        int absentDays = 0;
        int presentDays = presentDates.size();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            boolean isSunday = (date.getDayOfWeek() == DayOfWeek.SUNDAY);
            boolean isCompanyLeave = companyLeaveDates.contains(date);

            if (!isSunday && !isCompanyLeave) {
                totalWorkingDays++;

                // Count as absent only if: past date, no check-in record, AND no approved leave
                if (date.isBefore(today) && !presentDates.contains(date) && !onLeaveDates.contains(date)) {
                    absentDays++;
                }
            }
        }

        double percentage = totalWorkingDays > 0 ? ((double) presentDays / totalWorkingDays) * 100 : 0.0;

        return new AttendanceSummaryDto(startOfMonth, totalWorkingDays, presentDays, absentDays, percentage);
    }

    public List<MonthlyAttendanceDto> getMonthlyAttendance(User user, int year, int month) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // 1. Load company holidays and events
        List<CompanyLeave> companyLeaves = companyLeaveRepository.findByDateBetween(startOfMonth, endOfMonth);
        Map<LocalDate, CompanyLeave> holidayMap = new HashMap<>();
        for (CompanyLeave cl : companyLeaves) {
            holidayMap.put(cl.getDate(), cl);
        }

        // 2. Load actual attendance records
        List<AttendanceRecord> records = attendanceRepository.findByUserAndDateBetween(user, startOfMonth, endOfMonth);
        Map<LocalDate, AttendanceRecord> attendanceMap = new HashMap<>();
        for (AttendanceRecord r : records) {
            attendanceMap.put(r.getDate(), r);
        }

        // 3. Load APPROVED employee leave requests (may span multiple days)
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesInRange(user, startOfMonth,
                endOfMonth);
        // Build a map from each covered date to the leave type abbreviation
        Map<LocalDate, String> leaveTypeMap = new HashMap<>();
        for (LeaveRequest lr : approvedLeaves) {
            LocalDate d = lr.getFromDate();
            while (!d.isAfter(lr.getToDate())) {
                if (!d.isBefore(startOfMonth) && !d.isAfter(endOfMonth)) {
                    String typeName = lr.getLeaveType().getName().toLowerCase();
                    String code;
                    if (typeName.contains("sick") || typeName.equals("sl"))
                        code = "SL";
                    else if (typeName.contains("casual") || typeName.equals("cl"))
                        code = "CL";
                    else if (typeName.contains("privilege") || typeName.equals("pl"))
                        code = "PL";
                    else
                        code = "L"; // generic leave
                    leaveTypeMap.put(d, code);
                }
                d = d.plusDays(1);
            }
        }

        // 4. Build the result list, one entry per calendar day of the month
        List<MonthlyAttendanceDto> result = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            boolean isSunday = (date.getDayOfWeek() == DayOfWeek.SUNDAY);
            boolean isHoliday = holidayMap.containsKey(date);
            boolean isFuture = date.isAfter(today);

            String status;
            String signIn = null;
            String signOut = null;

            if (isSunday) {
                status = "O"; // Weekly Off
            } else if (isHoliday) {
                status = "H"; // Public Holiday
            } else if (leaveTypeMap.containsKey(date)) {
                status = leaveTypeMap.get(date); // SL, CL, PL etc.
            } else if (attendanceMap.containsKey(date)) {
                AttendanceRecord rec = attendanceMap.get(date);
                status = "P";
                if (rec.getCheckInTime() != null)
                    signIn = rec.getCheckInTime().format(timeFormatter);
                if (rec.getCheckOutTime() != null)
                    signOut = rec.getCheckOutTime().format(timeFormatter);
            } else if (!isFuture) {
                status = "A"; // Absent (past, no record)
            } else {
                status = null; // Future date — no status
            }

            result.add(new MonthlyAttendanceDto(date, status, "GEN", signIn, signOut));
        }

        return result;
    }
}

package com.hrms.backend.service;

import com.hrms.backend.dto.LeaveRequestDto;
import com.hrms.backend.model.*;
import com.hrms.backend.repository.*;
import com.hrms.backend.util.ShiftUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LeaveService {

    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private CompanyLeaveRepository companyLeaveRepository;
    @Autowired private com.hrms.backend.repository.AttendanceRepository attendanceRepository;
    @Autowired private com.hrms.backend.config.ShiftConfig shiftConfig;
    @Autowired private AttendanceAuditLogRepository auditLogRepository;

    // ─────────────────────────────────────────────────────────────────────────────
    // LEAVE TYPES
    // ─────────────────────────────────────────────────────────────────────────────

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public LeaveType createLeaveType(LeaveType leaveType) {
        return leaveTypeRepository.save(leaveType);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // LEAVE BALANCES
    // ─────────────────────────────────────────────────────────────────────────────

    public List<LeaveBalance> getBalancesForEmployee(User employee) {
        return leaveBalanceRepository.findByEmployee(employee);
    }

    public List<LeaveBalance> getAllBalances() {
        return leaveBalanceRepository.findAll();
    }

    @Transactional
    public LeaveBalance updateBalance(Long balanceId, double newRemainingDays) {
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElseThrow(() -> new RuntimeException("Leave balance record not found"));
        double difference = newRemainingDays - balance.getRemainingDays();
        balance.setTotalGranted(balance.getTotalGranted() + difference);
        balance.setRemainingDays(newRemainingDays);
        return leaveBalanceRepository.save(balance);
    }

    @Transactional
    public void deleteBalance(Long balanceId) {
        leaveBalanceRepository.deleteById(balanceId);
    }

    @Transactional
    public void grantLeaveBalance(List<Long> employeeIds, Long leaveTypeId, double amount) {
        LeaveType type = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new RuntimeException("LeaveType not found"));
        List<User> employees = userRepository.findAllById(employeeIds);
        for (User employee : employees) {
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeAndLeaveType(employee, type)
                    .orElse(new LeaveBalance(employee, type, 0));
            balance.setTotalGranted(balance.getTotalGranted() + amount);
            balance.setRemainingDays(balance.getRemainingDays() + amount);
            leaveBalanceRepository.save(balance);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // EMPLOYEE ACTIONS
    // ─────────────────────────────────────────────────────────────────────────────

    public List<LeaveRequest> getEmployeeLeaveHistory(User employee) {
        return leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(employee);
    }

    @Transactional
    public LeaveRequest applyForLeave(User employee, LeaveRequestDto dto) {
        LeaveType type = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new RuntimeException("Leave type not found"));

        LocalDate today = LocalDate.now();
        double requestedDays = calculateLeaveDays(dto.getFromDate(), dto.getToDate(),
                dto.getSessionFrom(), dto.getSessionTo());

        // ── A. WEEKEND CHECK ────────────────────────────────────────────────────
        for (LocalDate d = dto.getFromDate(); !d.isAfter(dto.getToDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw new RuntimeException(
                        "Selected date " + d + " falls on a Sunday (Weekly Off). " +
                        "Leave cannot be applied on a weekly off day.");
            }
        }

        // ── B. COMPANY HOLIDAY CHECK ────────────────────────────────────────────
        for (LocalDate d = dto.getFromDate(); !d.isAfter(dto.getToDate()); d = d.plusDays(1)) {
            final LocalDate finalD = d;
            boolean isHoliday = companyLeaveRepository.findByDateBetween(finalD, finalD)
                    .stream().anyMatch(h -> h.getDate().equals(finalD));
            if (isHoliday) {
                throw new RuntimeException(
                        "Selected date " + d + " is already a company holiday. " +
                        "Leave cannot be applied on a company holiday.");
            }
        }

        // ── C. LEAVE TYPE DATE RESTRICTIONS ────────────────────────────────────
        String typeName = type.getName().toLowerCase();

        if (isSickLeave(typeName)) {
            // SL: only today
            if (!dto.getFromDate().equals(today) || !dto.getToDate().equals(today)) {
                throw new RuntimeException(
                        "Sick Leave can only be applied for the current date (" + today + "). " +
                        "You cannot apply SL for past or future dates.");
            }
        } else if (isCasualLeave(typeName)) {
            // CL: today or future only
            if (dto.getFromDate().isBefore(today)) {
                throw new RuntimeException(
                        "Casual Leave cannot be applied for past dates. " +
                        "CL is allowed for today (" + today + ") or future dates only.");
            }
        }
        // PL: no date restriction — any past, current, or future date is allowed.

        // ── D. ATTENDANCE PRESENCE CHECK ─────────────────────────────────────────
        // Reject leave application if the employee was already marked Present or attended on any requested date
        LocalTime secondHalfStart = ShiftUtils.getSecondHalfStart(employee, shiftConfig);

        for (LocalDate d = dto.getFromDate(); !d.isAfter(dto.getToDate()); d = d.plusDays(1)) {
            Optional<AttendanceRecord> existingRecord = attendanceRepository.findByUserAndDate(employee, d);
            if (existingRecord.isPresent()) {
                AttendanceRecord record = existingRecord.get();
                String status = record.getStatus();

                // 1. Full-day "Present" check
                if (status != null && status.equalsIgnoreCase("Present")) {
                    if (d.isBefore(today)) {
                        throw new RuntimeException(
                                "Cannot apply leave for " + d + ": You were already marked Present on this day. " +
                                "Leave cannot be applied for dates you have already attended.");
                    } else if (d.equals(today)) {
                        if (record.getCheckOutTime() != null) {
                            throw new RuntimeException(
                                    "Cannot apply leave for today (" + d + "): You have already completed attendance for today.");
                        }
                        if (dto.getSessionFrom() == LeaveRequest.LeaveSession.FULL_DAY
                                || dto.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_1) {
                            throw new RuntimeException(
                                    "Cannot apply full-day or first-half leave for today (" + d + "): " +
                                    "You are already checked in and marked Present. Only Second Half leave may be applied if eligible.");
                        }
                    }
                }

                // 2. "Half Day Present" check
                if (status != null && status.equalsIgnoreCase("Half Day Present")) {
                    boolean isFullDayOnDate = (d.isAfter(dto.getFromDate()) && d.isBefore(dto.getToDate()))
                            || (d.equals(dto.getFromDate()) && dto.getSessionFrom() == LeaveRequest.LeaveSession.FULL_DAY)
                            || (d.equals(dto.getToDate()) && dto.getSessionTo() == LeaveRequest.LeaveSession.FULL_DAY);

                    if (isFullDayOnDate) {
                        throw new RuntimeException(
                                "Cannot apply full-day leave for " + d + ": You were already marked Half Day Present on this day.");
                    }

                    if (record.getCheckInTime() != null) {
                        boolean attendedSecondHalf = ShiftUtils.isCheckedInDuringSecondHalf(
                                record.getCheckInTime(), secondHalfStart);

                        if (!attendedSecondHalf) {
                            // Attended first half
                            if ((d.equals(dto.getFromDate()) && dto.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_1)
                                    || (d.equals(dto.getToDate()) && dto.getSessionTo() == LeaveRequest.LeaveSession.SESSION_1)) {
                                throw new RuntimeException(
                                        "Cannot apply first-half leave for " + d + ": You already attended the first half on this day.");
                            }
                        } else {
                            // Attended second half
                            if ((d.equals(dto.getFromDate()) && dto.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_2)
                                    || (d.equals(dto.getToDate()) && dto.getSessionTo() == LeaveRequest.LeaveSession.SESSION_2)) {
                                throw new RuntimeException(
                                        "Cannot apply second-half leave for " + d + ": You already attended the second half on this day.");
                            }
                        }
                    }
                }
            }
        }

        // ── E. SESSION TIME RESTRICTION ─────────────────────────────────────────
        // If applying for today after second-half has started, only SESSION_2 is allowed
        if (dto.getFromDate().equals(today) && LocalTime.now().isAfter(secondHalfStart)) {
            if (dto.getSessionFrom() == LeaveRequest.LeaveSession.FULL_DAY ||
                    dto.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_1) {
                throw new RuntimeException(
                        "The first half has already passed. " +
                        "You can only apply for Second Half leave now.");
            }
        }
        if (dto.getToDate().equals(today) && LocalTime.now().isAfter(secondHalfStart)) {
            if (dto.getSessionTo() == LeaveRequest.LeaveSession.FULL_DAY ||
                    dto.getSessionTo() == LeaveRequest.LeaveSession.SESSION_1) {
                throw new RuntimeException(
                        "The first half has already passed for today's end date. " +
                        "You can only apply for Second Half leave now.");
            }
        }

        // ── F. SECTION 9: Reject SESSION_2 leave if already checked in during 2nd half ──
        // Only applicable for single-day SESSION_2 requests for today or past dates
        if (dto.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_2
                && dto.getFromDate().equals(dto.getToDate())) {
            Optional<AttendanceRecord> existingRecord =
                    attendanceRepository.findByUserAndDate(employee, dto.getFromDate());
            if (existingRecord.isPresent() && existingRecord.get().getCheckInTime() != null) {
                LocalTime checkIn = existingRecord.get().getCheckInTime();
                if (ShiftUtils.isCheckedInDuringSecondHalf(checkIn, secondHalfStart)) {
                    throw new RuntimeException(
                            "You have already checked in during the second half (at " + checkIn + "). " +
                            "Second-half leave cannot be applied for a period you are already attending.");
                }
            }
        }

        // ── G. OVERLAP CHECK ────────────────────────────────────────────────────
        // Enhanced: also check half-day session overlap on same date
        validateNoOverlap(employee, dto);

        // ── G. BALANCE CHECK ────────────────────────────────────────────────────
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeAndLeaveType(employee, type)
                .orElseThrow(() -> new RuntimeException(
                        "No leave balance record found for " + type.getName() + ". Please contact HR."));

        if (balance.getRemainingDays() < requestedDays) {
            throw new RuntimeException(
                    "Insufficient " + type.getName() + " balance. " +
                    "You have " + balance.getRemainingDays() + " days remaining but requested " + requestedDays + " days.");
        }

        // ── SAVE ────────────────────────────────────────────────────────────────
        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setLeaveType(type);
        request.setFromDate(dto.getFromDate());
        request.setToDate(dto.getToDate());
        request.setSessionFrom(dto.getSessionFrom());
        request.setSessionTo(dto.getSessionTo());
        request.setReason(dto.getReason());
        request.setStatus(LeaveRequest.LeaveStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(request);

        sendNotification("/topic/admin/leaves", "New Leave Request",
                employee.getFirstName() + " applied for " + type.getName() + " (" + requestedDays + " days)");

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMIN ACTIONS
    // ─────────────────────────────────────────────────────────────────────────────

    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<LeaveRequest> getPendingRequests() {
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc(LeaveRequest.LeaveStatus.PENDING);
    }

    @Transactional
    public LeaveRequest approveLeave(Long requestId, User admin) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (request.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Only PENDING requests can be approved.");
        }

        // Validate that the employee was not marked "Present" on any date in the request
        LocalDate today = LocalDate.now();
        for (LocalDate d = request.getFromDate(); !d.isAfter(request.getToDate()); d = d.plusDays(1)) {
            Optional<AttendanceRecord> optRecord = attendanceRepository.findByUserAndDate(request.getEmployee(), d);
            if (optRecord.isPresent()) {
                AttendanceRecord rec = optRecord.get();
                if (rec.getStatus() != null && rec.getStatus().equalsIgnoreCase("Present")) {
                    if (d.isBefore(today) || rec.getCheckOutTime() != null) {
                        throw new RuntimeException(
                                "Cannot approve leave: Employee was already marked Present on " + d + ". " +
                                "Leave cannot be approved for days already attended.");
                    }
                }
            }
        }

        double daysToDeduct = calculateLeaveDays(request.getFromDate(), request.getToDate(),
                request.getSessionFrom(), request.getSessionTo());

        // Re-validate balance at approval time (may have changed since application)
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveType(request.getEmployee(), request.getLeaveType())
                .orElseThrow(() -> new RuntimeException("Leave balance record not found."));

        if (balance.getRemainingDays() < daysToDeduct) {
            throw new RuntimeException(
                    "Employee does not have enough " + request.getLeaveType().getName() +
                    " balance to approve this leave. Available: " + balance.getRemainingDays() +
                    " days, Required: " + daysToDeduct + " days.");
        }

        balance.setUsedDays(balance.getUsedDays() + daysToDeduct);
        balance.setRemainingDays(balance.getRemainingDays() - daysToDeduct);
        leaveBalanceRepository.save(balance);

        request.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        request.setApprovedBy(admin);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Sync attendance
        handleLeaveAttendanceIntegration(saved, admin);

        sendNotification("/topic/reminders", "Leave Approved",
                "Your " + request.getLeaveType().getName() + " request has been approved.");

        return saved;
    }

    @Transactional
    public LeaveRequest rejectLeave(Long requestId, User admin) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (request.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Only PENDING requests can be rejected.");
        }

        request.setStatus(LeaveRequest.LeaveStatus.REJECTED);
        request.setApprovedBy(admin);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Rejection: attendance should NOT have been mutated for a pending request
        // (check-in gating only applies to APPROVED leaves, not pending).
        // Log the rejection action for audit trail.
        String adminName = admin != null ? admin.getUsername() : "admin";
        LocalDate d = request.getFromDate();
        while (!d.isAfter(request.getToDate())) {
            Optional<AttendanceRecord> rec = attendanceRepository.findByUserAndDate(request.getEmployee(), d);
            AttendanceAuditLog auditEntry = new AttendanceAuditLog();
            auditEntry.setUser(request.getEmployee());
            auditEntry.setAttendanceDate(d);
            auditEntry.setLeaveRequestId(requestId);
            auditEntry.setAction(AttendanceAuditLog.AuditAction.LEAVE_REJECTED);
            auditEntry.setPreviousStatus(rec.map(AttendanceRecord::getStatus).orElse(null));
            auditEntry.setNewStatus(rec.map(AttendanceRecord::getStatus).orElse(null));
            auditEntry.setMutatedBy(adminName);
            auditLogRepository.save(auditEntry);
            d = d.plusDays(1);
        }

        sendNotification("/topic/reminders", "Leave Rejected",
                "Your " + request.getLeaveType().getName() + " request was rejected.");

        return saved;
    }

    @Transactional
    public LeaveRequest cancelLeave(Long requestId, User requester) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (request.getStatus() == LeaveRequest.LeaveStatus.CANCELLED) {
            throw new RuntimeException("Leave request is already cancelled.");
        }

        // Allow cancellation of PENDING and APPROVED requests
        boolean wasApproved = request.getStatus() == LeaveRequest.LeaveStatus.APPROVED;

        if (wasApproved) {
            // Restore balance
            double daysToRestore = calculateLeaveDays(request.getFromDate(), request.getToDate(),
                    request.getSessionFrom(), request.getSessionTo());
            leaveBalanceRepository.findByEmployeeAndLeaveType(request.getEmployee(), request.getLeaveType())
                    .ifPresent(balance -> {
                        balance.setUsedDays(Math.max(0, balance.getUsedDays() - daysToRestore));
                        balance.setRemainingDays(balance.getRemainingDays() + daysToRestore);
                        leaveBalanceRepository.save(balance);
                    });

            // Revert attendance mutations caused by this leave approval
            revertAttendanceMutations(requestId, requester);
        }

        request.setStatus(LeaveRequest.LeaveStatus.CANCELLED);
        LeaveRequest saved = leaveRequestRepository.save(request);

        sendNotification("/topic/reminders", "Leave Cancelled",
                "Your " + request.getLeaveType().getName() + " leave has been cancelled.");

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Core logic: syncs attendance records when a leave is approved.
     * Handles full-day, first-half (SESSION_1), second-half (SESSION_2), and retroactive scenarios.
     */
    private void handleLeaveAttendanceIntegration(LeaveRequest request, User admin) {
        User employee = request.getEmployee();
        LocalTime secondHalfStart = ShiftUtils.getSecondHalfStart(employee, shiftConfig);
        String adminName = admin != null ? admin.getUsername() : "system";
        String leaveCode = leaveTypeCode(request.getLeaveType().getName());

        for (LocalDate date = request.getFromDate(); !date.isAfter(request.getToDate()); date = date.plusDays(1)) {

            boolean isFullDay = ShiftUtils.isFullDayLeave(request, date);
            boolean isSession2 = ShiftUtils.isSecondHalfLeave(request, date);
            boolean isSession1 = ShiftUtils.isFirstHalfLeave(request, date);

            Optional<AttendanceRecord> optRecord = attendanceRepository.findByUserAndDate(employee, date);
            AttendanceRecord record = optRecord.orElse(null);

            // Capture before-state for audit
            String prevStatus = record != null ? record.getStatus() : null;
            LocalTime prevCheckOut = record != null ? record.getCheckOutTime() : null;
            boolean recordCreated = false;

            if (isFullDay) {
                // ── FULL DAY LEAVE ────────────────────────────────────────────────────
                if (record == null) {
                    // No attendance record: create one with leave status
                    record = new AttendanceRecord();
                    record.setUser(employee);
                    record.setDate(date);
                    record.setStatus(leaveCode);
                    record.setLeaveNote("Full-day " + leaveCode + " (retroactive)");
                    recordCreated = true;
                } else if ("Present".equalsIgnoreCase(record.getStatus())) {
                    // Date already has a fully-worked present record — do NOT overwrite
                    // Log and skip
                    writeAuditLog(employee, date, request.getId(), AttendanceAuditLog.AuditAction.LEAVE_APPROVED,
                            prevStatus, prevCheckOut, prevStatus, prevCheckOut, false, adminName,
                            "Skipped: date already Present, not overwritten by " + leaveCode);
                    continue;
                } else {
                    // Absent or other non-present status: convert to leave
                    record.setStatus(leaveCode);
                    record.setLeaveNote("Full-day " + leaveCode);
                }
                attendanceRepository.save(record);

            } else if (isSession2) {
                // ── SECOND HALF LEAVE ─────────────────────────────────────────────────
                if (record != null && record.getCheckInTime() != null) {
                    LocalTime checkIn = record.getCheckInTime();

                    // Section 9: if check-in is >= secondHalfStart, reject the approval for this day
                    if (ShiftUtils.isCheckedInDuringSecondHalf(checkIn, secondHalfStart)) {
                        // This should have been caught at application time, but guard at approval too
                        throw new RuntimeException(
                                "Cannot approve second-half leave for " + date +
                                ": employee already checked in during the second half (at " + checkIn + ").");
                    }

                    // Checked in during first half — apply auto-checkout at second-half start
                    if (record.getCheckOutTime() == null) {
                        // No checkout yet: set to second-half start (auto-checkout)
                        record.setCheckOutTime(secondHalfStart);
                        record.setLeaveNote("SESSION_2 " + leaveCode + " auto-checkout at " + secondHalfStart);
                    } else {
                        // Existing checkout: if checkout is AFTER secondHalfStart, cap it
                        // If checkout is BEFORE secondHalfStart, preserve it (employee left early)
                        if (record.getCheckOutTime().isAfter(secondHalfStart)) {
                            record.setCheckOutTime(secondHalfStart);
                            record.setLeaveNote("SESSION_2 " + leaveCode + " checkout capped to " + secondHalfStart);
                        } else {
                            record.setLeaveNote("SESSION_2 " + leaveCode + " (early manual checkout preserved)");
                        }
                    }
                    record.setStatus("Half Day Present");
                } else if (record == null) {
                    // No attendance record at all for this date — create a half-day leave record
                    record = new AttendanceRecord();
                    record.setUser(employee);
                    record.setDate(date);
                    record.setStatus("Half Day Present");
                    record.setLeaveNote("SESSION_2 " + leaveCode + " (no check-in)");
                    recordCreated = true;
                }
                // If record exists but no checkInTime, just update status
                if (record != null && record.getCheckInTime() == null && !recordCreated) {
                    record.setStatus("Half Day Present");
                    record.setLeaveNote("SESSION_2 " + leaveCode);
                }
                if (record != null) {
                    attendanceRepository.save(record);
                }

            } else if (isSession1) {
                // ── FIRST HALF LEAVE ──────────────────────────────────────────────────
                if (record != null && record.getCheckInTime() != null) {
                    LocalTime checkIn = record.getCheckInTime();
                    if (!ShiftUtils.isCheckedInDuringSecondHalf(checkIn, secondHalfStart)) {
                        // Checked in during first half — apply auto-checkout at second-half start
                        if (record.getCheckOutTime() == null) {
                            record.setCheckOutTime(secondHalfStart);
                            record.setFirstCheckOutTime(secondHalfStart);
                            record.setLeaveNote("SESSION_1 " + leaveCode + " auto-checkout at " + secondHalfStart);
                        } else {
                            if (record.getCheckOutTime().isAfter(secondHalfStart)) {
                                record.setCheckOutTime(secondHalfStart);
                                record.setFirstCheckOutTime(secondHalfStart);
                                record.setLeaveNote("SESSION_1 " + leaveCode + " checkout capped to " + secondHalfStart);
                            } else {
                                record.setFirstCheckOutTime(record.getCheckOutTime());
                                record.setLeaveNote("SESSION_1 " + leaveCode + " (early manual checkout preserved)");
                            }
                        }
                    } else {
                        // Checked in during second half (normal second-half work after morning leave)
                        record.setLeaveNote("SESSION_1 " + leaveCode + " (second-half work at " + checkIn + ")");
                    }
                    record.setStatus("Half Day Present");
                } else if (record == null) {
                    // No check-in: create half-day attendance record
                    record = new AttendanceRecord();
                    record.setUser(employee);
                    record.setDate(date);
                    record.setStatus("Half Day Present");
                    record.setLeaveNote("SESSION_1 " + leaveCode + " (no check-in)");
                    recordCreated = true;
                } else {
                    // Record exists but no check-in
                    record.setStatus("Half Day Present");
                    record.setLeaveNote("SESSION_1 " + leaveCode);
                }
                if (record != null) {
                    attendanceRepository.save(record);
                }
            }

            // Write audit log
            String afterStatus = record != null ? record.getStatus() : null;
            LocalTime afterCheckOut = record != null ? record.getCheckOutTime() : null;
            writeAuditLog(employee, date, request.getId(), AttendanceAuditLog.AuditAction.LEAVE_APPROVED,
                    prevStatus, prevCheckOut, afterStatus, afterCheckOut, recordCreated, adminName, null);
        }
    }

    /**
     * Reverts all attendance mutations caused by the given leave request.
     * Used when an approved leave is cancelled.
     */
    private void revertAttendanceMutations(Long requestId, User requester) {
        List<AttendanceAuditLog> auditEntries =
                auditLogRepository.findByLeaveRequestIdOrderByMutatedAtDesc(requestId);

        String requesterName = requester != null ? requester.getUsername() : "system";

        for (AttendanceAuditLog entry : auditEntries) {
            if (entry.getAction() != AttendanceAuditLog.AuditAction.LEAVE_APPROVED) continue;

            Optional<AttendanceRecord> optRecord =
                    attendanceRepository.findByUserAndDate(entry.getUser(), entry.getAttendanceDate());

            if (entry.isAttendanceRecordCreated()) {
                // Record was created by the leave approval — delete it on cancel
                optRecord.ifPresent(r -> attendanceRepository.delete(r));
            } else {
                // Record was modified — restore previous state
                optRecord.ifPresent(record -> {
                    record.setStatus(entry.getPreviousStatus());
                    record.setCheckOutTime(entry.getPreviousCheckOutTime());
                    record.setLeaveNote(null);
                    attendanceRepository.save(record);
                });
            }

            // Write revert audit entry
            writeAuditLog(entry.getUser(), entry.getAttendanceDate(), requestId,
                    AttendanceAuditLog.AuditAction.LEAVE_APPROVAL_REVERTED,
                    entry.getNewStatus(), entry.getNewCheckOutTime(),
                    entry.getPreviousStatus(), entry.getPreviousCheckOutTime(),
                    false, requesterName, "Leave cancelled");
        }
    }

    private void writeAuditLog(User user, LocalDate date, Long leaveRequestId,
                                AttendanceAuditLog.AuditAction action,
                                String prevStatus, LocalTime prevCheckOut,
                                String newStatus, LocalTime newCheckOut,
                                boolean created, String mutatedBy, String note) {
        AttendanceAuditLog entry = new AttendanceAuditLog();
        entry.setUser(user);
        entry.setAttendanceDate(date);
        entry.setLeaveRequestId(leaveRequestId);
        entry.setAction(action);
        entry.setPreviousStatus(prevStatus);
        entry.setPreviousCheckOutTime(prevCheckOut);
        entry.setNewStatus(newStatus);
        entry.setNewCheckOutTime(newCheckOut);
        entry.setAttendanceRecordCreated(created);
        entry.setMutatedBy(mutatedBy);
        auditLogRepository.save(entry);
    }

    /**
     * Validates no overlapping leave requests (including half-day same-date awareness).
     */
    private void validateNoOverlap(User employee, LeaveRequestDto dto) {
        long overlapping = leaveRequestRepository.countOverlappingLeaves(
                employee, dto.getFromDate(), dto.getToDate());
        if (overlapping > 0) {
            // For a single-day half-day request, check if the overlap is only on the other half
            if (dto.getFromDate().equals(dto.getToDate())
                    && dto.getSessionFrom() != LeaveRequest.LeaveSession.FULL_DAY) {
                // Check for existing half-day request on the same half for this date
                List<LeaveRequest> existing = leaveRequestRepository.findApprovedOrPendingForDate(
                        employee, dto.getFromDate());
                for (LeaveRequest ex : existing) {
                    if (ex.getFromDate().equals(dto.getFromDate()) && ex.getToDate().equals(dto.getFromDate())) {
                        if (ex.getSessionFrom() == LeaveRequest.LeaveSession.FULL_DAY) {
                            throw new RuntimeException(
                                    "You already have a full-day leave applied for " + dto.getFromDate() + ".");
                        }
                        if (ex.getSessionFrom() == dto.getSessionFrom()) {
                            throw new RuntimeException(
                                    "You already have a " + sessionLabel(dto.getSessionFrom()) +
                                    " leave applied for " + dto.getFromDate() + ".");
                        }
                        // Different halves (SESSION_1 + SESSION_2) on same date — reject (would make full day)
                        throw new RuntimeException(
                                "You already have a leave applied for a portion of " + dto.getFromDate() + ". " +
                                "Combining two half-day leaves on the same date is not allowed.");
                    } else {
                        throw new RuntimeException(
                                "You already have a leave applied on dates overlapping with " +
                                dto.getFromDate() + " to " + dto.getToDate() + ".");
                    }
                }
            } else {
                throw new RuntimeException(
                        "You already have a leave applied on dates overlapping with " +
                        dto.getFromDate() + " to " + dto.getToDate() + ".");
            }
        }
    }

    private String sessionLabel(LeaveRequest.LeaveSession session) {
        return switch (session) {
            case SESSION_1 -> "first-half";
            case SESSION_2 -> "second-half";
            default -> "full-day";
        };
    }

    public double calculateLeaveDays(java.time.LocalDate fromDate, java.time.LocalDate toDate,
            LeaveRequest.LeaveSession sessionFrom, LeaveRequest.LeaveSession sessionTo) {
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (daysBetween <= 0) return 0;

        if (daysBetween == 1) {
            return sessionFrom != LeaveRequest.LeaveSession.FULL_DAY ? 0.5 : 1.0;
        } else {
            double days = daysBetween;
            if (sessionFrom == LeaveRequest.LeaveSession.SESSION_2) days -= 0.5;
            if (sessionTo == LeaveRequest.LeaveSession.SESSION_1) days -= 0.5;
            return days;
        }
    }

    private void sendNotification(String topic, String title, String message) {
        Map<String, String> notification = new HashMap<>();
        notification.put("type", "LEAVE_UPDATE");
        notification.put("title", title);
        notification.put("message", message);
        notification.put("time",
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")));
        messagingTemplate.convertAndSend(topic, notification);
    }

    // ── Leave type helpers ───────────────────────────────────────────────────────

    public static boolean isSickLeave(String typeName) {
        return typeName.contains("sick") || typeName.equals("sl");
    }

    public static boolean isCasualLeave(String typeName) {
        return typeName.contains("casual") || typeName.equals("cl");
    }

    public static boolean isPrivilegeLeave(String typeName) {
        return typeName.contains("privilege") || typeName.contains("paid") || typeName.equals("pl");
    }

    private String leaveTypeCode(String leaveTypeName) {
        String lower = leaveTypeName.toLowerCase();
        if (isSickLeave(lower)) return "SL";
        if (isCasualLeave(lower)) return "CL";
        if (isPrivilegeLeave(lower)) return "PL";
        return "L";
    }
}

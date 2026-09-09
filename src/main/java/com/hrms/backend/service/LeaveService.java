package com.hrms.backend.service;

import com.hrms.backend.dto.LeaveRequestDto;
import com.hrms.backend.model.LeaveBalance;
import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.LeaveType;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.CompanyLeaveRepository;
import com.hrms.backend.repository.LeaveBalanceRepository;
import com.hrms.backend.repository.LeaveRequestRepository;
import com.hrms.backend.repository.LeaveTypeRepository;
import com.hrms.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaveService {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private CompanyLeaveRepository companyLeaveRepository;

    // --- Leave Types ---
    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public LeaveType createLeaveType(LeaveType leaveType) {
        return leaveTypeRepository.save(leaveType);
    }

    // --- Leave Balances ---
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

        // When admin specifies a new remaining balance explicitly:
        // Adjust the total granted to match, keeping usedDays intact.
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

            // Add the granted amount to total granted and remaining days
            balance.setTotalGranted(balance.getTotalGranted() + amount);
            balance.setRemainingDays(balance.getRemainingDays() + amount);
            leaveBalanceRepository.save(balance);
        }
    }

    // --- Employee Actions ---
    public List<LeaveRequest> getEmployeeLeaveHistory(User employee) {
        return leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(employee);
    }

    @Transactional
    public LeaveRequest applyForLeave(User employee, LeaveRequestDto dto) {
        LeaveType type = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new RuntimeException("Leave type not found"));

        double requestedDays = calculateLeaveDays(dto.getFromDate(), dto.getToDate(), dto.getSessionFrom(),
                dto.getSessionTo());

        // --- PRE-FLIGHT VALIDATIONS (run before type-specific policies) ---

        // A. Leave Overlap Check: Employee cannot have another leave on the same dates
        long overlapping = leaveRequestRepository.countOverlappingLeaves(
                employee, dto.getFromDate(), dto.getToDate());
        if (overlapping > 0) {
            throw new RuntimeException(
                    "You already have a leave applied on this date.\n" +
                            "Multiple leave types cannot be applied for the same day.");
        }

        // B. Company Holiday Conflict Check
        LocalDate checkDate = dto.getFromDate();
        while (!checkDate.isAfter(dto.getToDate())) {
            LocalDate finalCheckDate = checkDate;
            boolean isHoliday = companyLeaveRepository.findByDateBetween(finalCheckDate, finalCheckDate)
                    .stream().anyMatch(h -> h.getDate().equals(finalCheckDate));
            if (isHoliday) {
                throw new RuntimeException(
                        "Selected date " + checkDate + " is already a company holiday.\n" +
                                "Leave cannot be applied on a company holiday.");
            }
            checkDate = checkDate.plusDays(1);
        }

        // C. Weekly Off Conflict Check (Sunday)
        checkDate = dto.getFromDate();
        while (!checkDate.isAfter(dto.getToDate())) {
            if (checkDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw new RuntimeException(
                        "Selected date " + checkDate + " falls on a Sunday (Weekly Off).\n" +
                                "Leave cannot be applied on a weekly off day.");
            }
            checkDate = checkDate.plusDays(1);
        }

        // --- POLICY VALIDATIONS ---
        String typeName = type.getName().toLowerCase();

        // 1. Sick Leave (SL): Must be for the current date only
        if (typeName.contains("sick") || typeName.equals("sl")) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (!dto.getFromDate().equals(today) || !dto.getToDate().equals(today)) {
                throw new RuntimeException("Sick leave can only be applied for the current date.");
            }
        }

        // 2. Casual Leave (CL) & Privilege Leave (PL): Only 1 per month
        if (typeName.contains("casual") || typeName.equals("cl") ||
                typeName.contains("privilege") || typeName.equals("pl")) {

            java.time.LocalDate startOfMonth = dto.getFromDate().withDayOfMonth(1);
            java.time.LocalDate endOfMonth = dto.getFromDate().withDayOfMonth(dto.getFromDate().lengthOfMonth());

            long currentMonthLeaves = leaveRequestRepository.countLeavesInMonthForType(employee, type, startOfMonth,
                    endOfMonth);
            if (currentMonthLeaves >= 1) {
                // Return exact message required by prompt
                throw new RuntimeException("You have reached the " + type.getName() + " limit for this month.");
            }
        }

        // 3. Time-based Session Restriction: If applied for today after 12:00 PM, only
        // allow SECOND_HALF
        if (dto.getFromDate().equals(java.time.LocalDate.now())) {
            if (java.time.LocalTime.now().isAfter(java.time.LocalTime.of(12, 0))) {
                if (dto.getSessionFrom() == LeaveRequest.LeaveSession.FULL_DAY ||
                        dto.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_1) {
                    throw new RuntimeException(
                            "You cannot apply full-day sick leave after the first half has passed.\nPlease select Second Half.");
                }
            }
        }

        if (dto.getToDate().equals(java.time.LocalDate.now())) {
            if (java.time.LocalTime.now().isAfter(java.time.LocalTime.of(12, 0))) {
                if (dto.getSessionTo() == LeaveRequest.LeaveSession.FULL_DAY ||
                        dto.getSessionTo() == LeaveRequest.LeaveSession.SESSION_1) {
                    throw new RuntimeException(
                            "You cannot apply full-day leave ending today after the first half has passed.\nPlease select Second Half.");
                }
            }
        }

        // Optional: Check balance here before allowing application
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeAndLeaveType(employee, type)
                .orElseThrow(
                        () -> new RuntimeException("No leave balance record found for this type. Please contact HR."));

        if (balance.getRemainingDays() < requestedDays) {
            throw new RuntimeException("Insufficient leave balance for this request.");
        }

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

        // Notify Admin
        sendNotification("/topic/admin/leaves", "New Leave Request",
                employee.getFirstName() + " applied for " + type.getName() + " (" + requestedDays + " days)");

        return saved;
    }

    // --- Admin Actions ---
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
            throw new RuntimeException("Only pending requests can be approved");
        }

        // Deduct balance
        double daysToDeduct = calculateLeaveDays(request.getFromDate(), request.getToDate(), request.getSessionFrom(),
                request.getSessionTo());
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveType(request.getEmployee(), request.getLeaveType())
                .orElseThrow(() -> new RuntimeException("Balance record missing"));

        if (balance.getRemainingDays() < daysToDeduct) {
            throw new RuntimeException("Employee does not have enough balance to approve this leave");
        }

        balance.setUsedDays(balance.getUsedDays() + daysToDeduct);
        balance.setRemainingDays(balance.getRemainingDays() - daysToDeduct);
        leaveBalanceRepository.save(balance);

        request.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        request.setApprovedBy(admin);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify Employee
        // Using a broadcast since users don't have dedicated user queues right now,
        // frontend can filter by employeeId if needed, or simply send to a common topic
        sendNotification("/topic/reminders", "Leave Approved",
                "Your leave request for " + request.getLeaveType().getName() + " has been approved.");

        return saved;
    }

    @Transactional
    public LeaveRequest rejectLeave(Long requestId, User admin) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (request.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Only pending requests can be rejected");
        }

        request.setStatus(LeaveRequest.LeaveStatus.REJECTED);
        request.setApprovedBy(admin);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify Employee
        sendNotification("/topic/reminders", "Leave Rejected",
                "Your leave request for " + request.getLeaveType().getName() + " was rejected.");

        return saved;
    }

    // --- Helpers ---
    private double calculateLeaveDays(java.time.LocalDate fromDate, java.time.LocalDate toDate,
            LeaveRequest.LeaveSession sessionFrom, LeaveRequest.LeaveSession sessionTo) {
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (daysBetween <= 0)
            return 0;

        double days = daysBetween;

        if (daysBetween == 1) {
            // For a single day leave, sessionFrom dictates the day's total duration.
            if (sessionFrom != LeaveRequest.LeaveSession.FULL_DAY) {
                return 0.5;
            } else {
                return 1.0;
            }
        } else {
            // Multi-day leave: deduct 0.5 for each end that is a half-day session
            if (sessionFrom == LeaveRequest.LeaveSession.SESSION_2) {
                days -= 0.5; // Started late (Second Half), deduct morning
            } else if (sessionFrom == LeaveRequest.LeaveSession.SESSION_1) {
                // Starting First Half means they take the whole day, no deduction needed for
                // Start Day
            }

            if (sessionTo == LeaveRequest.LeaveSession.SESSION_1) {
                days -= 0.5; // Ended early (First Half), deduct afternoon
            } else if (sessionTo == LeaveRequest.LeaveSession.SESSION_2) {
                // Ending Second Half means they take the whole day, no deduction needed for End
                // Day
            }
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
}

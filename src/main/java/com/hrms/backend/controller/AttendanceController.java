package com.hrms.backend.controller;

import com.hrms.backend.dto.CheckInRequest;
import com.hrms.backend.dto.MessageResponse;
import com.hrms.backend.dto.MonthlyAttendanceDto;
import com.hrms.backend.model.AttendanceRecord;
import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.AttendanceRepository;
import com.hrms.backend.repository.LeaveRequestRepository;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.security.UserDetailsImpl;
import com.hrms.backend.service.AttendanceService;
import com.hrms.backend.util.GeoUtils;
import com.hrms.backend.util.ShiftUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final GeoUtils geoUtils;
    private final AttendanceService attendanceService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final com.hrms.backend.config.ShiftConfig shiftConfig;

    @Value("${app.geofence.office.latitude}")
    private double officeLat;

    @Value("${app.geofence.office.longitude}")
    private double officeLon;

    @Value("${app.geofence.radius.meters}")
    private double geofenceRadius;

    public AttendanceController(AttendanceRepository attendanceRepository, UserRepository userRepository,
            GeoUtils geoUtils, AttendanceService attendanceService,
            LeaveRequestRepository leaveRequestRepository,
            com.hrms.backend.config.ShiftConfig shiftConfig) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.geoUtils = geoUtils;
        this.attendanceService = attendanceService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.shiftConfig = shiftConfig;
    }

    private User getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Error: User is not found."));
    }

    private boolean isWithinGeofence(double userLat, double userLon) {
        double distance = geoUtils.calculateDistance(officeLat, officeLon, userLat, userLon);
        return distance <= geofenceRadius;
    }

    /**
     * Returns today's leave and attendance status for the current employee.
     * Used by the frontend to gate Check-In / Check-Out buttons correctly
     * without duplicating backend policy in the client.
     */
    @GetMapping("/today-status")
    public ResponseEntity<Map<String, Object>> getTodayStatus() {
        User user = getCurrentUser();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime secondHalfStart = ShiftUtils.getSecondHalfStart(user, shiftConfig);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a");

        // Only check APPROVED leaves — pending leaves must not affect attendance
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesInRange(user, today, today);
        Optional<AttendanceRecord> optRecord = attendanceRepository.findByUserAndDate(user, today);

        LeaveRequest.LeaveSession leaveSession = null;
        String leaveTypeName = null;
        for (LeaveRequest lr : approvedLeaves) {
            if (ShiftUtils.isFullDayLeave(lr, today)) {
                leaveSession = LeaveRequest.LeaveSession.FULL_DAY;
                leaveTypeName = lr.getLeaveType().getName();
                break;
            } else if (ShiftUtils.isSecondHalfLeave(lr, today)) {
                leaveSession = LeaveRequest.LeaveSession.SESSION_2;
                leaveTypeName = lr.getLeaveType().getName();
                break;
            } else if (ShiftUtils.isFirstHalfLeave(lr, today)) {
                leaveSession = LeaveRequest.LeaveSession.SESSION_1;
                leaveTypeName = lr.getLeaveType().getName();
                break;
            }
        }

        boolean hasCheckIn = optRecord.isPresent() && optRecord.get().getCheckInTime() != null;
        boolean hasCheckOut = hasCheckIn && optRecord.get().getCheckOutTime() != null;

        boolean canCheckIn;
        boolean canCheckOut;
        String reason = null;
        String nextAvailableAt = null;

        if (leaveSession == LeaveRequest.LeaveSession.FULL_DAY) {
            // Full-day leave: no check-in or check-out allowed
            canCheckIn = false;
            canCheckOut = false;
            reason = "You have an approved full-day " + leaveTypeName + " today. Check-in is not allowed.";

        } else if (leaveSession == LeaveRequest.LeaveSession.SESSION_1) {
            // First-half leave: check-in only allowed from second-half start time
            boolean isAutoCheckedOutFromFirstHalf = hasCheckOut &&
                    optRecord.isPresent() &&
                    optRecord.get().getCheckInTime() != null &&
                    optRecord.get().getCheckInTime().isBefore(secondHalfStart) &&
                    (secondHalfStart.equals(optRecord.get().getCheckOutTime()) || optRecord.get().getFirstCheckOutTime() != null) &&
                    optRecord.get().getSecondCheckInTime() == null;

            if (isAutoCheckedOutFromFirstHalf) {
                if (now.isBefore(secondHalfStart)) {
                    canCheckIn = false;
                    canCheckOut = false;
                    reason = "First-half leave is approved and checkout has been recorded. Check-in for second half available from " + secondHalfStart.format(fmt) + ".";
                    nextAvailableAt = secondHalfStart.format(fmt);
                } else {
                    // Second half has started! Allow check-in for second half
                    canCheckIn = true;
                    canCheckOut = false;
                    reason = null;
                }
            } else if (hasCheckOut) {
                // Completed actual checkout (e.g. worked second half and checked out)
                canCheckIn = false;
                canCheckOut = false;
                reason = "You have already completed attendance for today.";
            } else if (hasCheckIn) {
                // Currently checked in
                canCheckIn = false;
                canCheckOut = true;
            } else if (now.isBefore(secondHalfStart)) {
                canCheckIn = false;
                canCheckOut = false;
                reason = "First-half leave is approved. Check-in is available from " + secondHalfStart.format(fmt) + ".";
                nextAvailableAt = secondHalfStart.format(fmt);
            } else {
                canCheckIn = true;
                canCheckOut = false;
            }

        } else if (leaveSession == LeaveRequest.LeaveSession.SESSION_2) {
            // Second-half leave: check-in allowed before second half start only if not checked in yet
            if (hasCheckOut) {
                // Auto-checkout has been generated (leave approved) — no more activity
                canCheckIn = false;
                canCheckOut = false;
                reason = "Second-half leave is approved and checkout has been recorded. No further check-in today.";
            } else if (hasCheckIn) {
                // Checked in during first half, second half is leave — can still check out early (before second half)
                canCheckIn = false;
                reason = "You are already checked in.";
                canCheckOut = !hasCheckOut && now.isBefore(secondHalfStart);
            } else {
                // Not checked in yet — only allowed if before second half
                canCheckIn = !now.isAfter(secondHalfStart);
                canCheckOut = false;
                if (!canCheckIn) {
                    reason = "Second-half leave is active. Check-in is not allowed during the second half.";
                }
            }

        } else {
            // No leave today — normal rules
            canCheckIn = !hasCheckIn;
            canCheckOut = hasCheckIn && !hasCheckOut;
            if (hasCheckIn && !hasCheckOut) {
                reason = null; // Can check out
            } else if (hasCheckOut) {
                reason = "You have already completed attendance for today.";
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("canCheckIn", canCheckIn);
        result.put("canCheckOut", canCheckOut);
        result.put("reason", reason);
        result.put("nextAvailableAt", nextAvailableAt);
        result.put("leaveSession", leaveSession != null ? leaveSession.name() : null);
        result.put("leaveTypeName", leaveTypeName);
        result.put("secondHalfStartTime", secondHalfStart.format(fmt));
        result.put("hasCheckIn", hasCheckIn);
        result.put("hasCheckOut", hasCheckOut);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest request) {
        if (!isWithinGeofence(request.getLatitude(), request.getLongitude())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: You are outside the office geofence radius."));
        }

        User user = getCurrentUser();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime secondHalfStart = ShiftUtils.getSecondHalfStart(user, shiftConfig);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a");

        // ── Check approved leaves — ONLY APPROVED (not pending) ──────────────────
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesInRange(user, today, today);
        String initialStatus = "Present";
        boolean isFirstHalfLeave = false;
        LeaveRequest firstHalfLeaveReq = null;

        for (LeaveRequest lr : approvedLeaves) {
            if (ShiftUtils.isFullDayLeave(lr, today)) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse(
                                "Error: You have an approved full-day " + lr.getLeaveType().getName() +
                                " today. Check-in is not allowed."));
            }
            if (ShiftUtils.isFirstHalfLeave(lr, today)) {
                // SESSION_1 leave: check-in only allowed from second-half start
                if (now.isBefore(secondHalfStart)) {
                    return ResponseEntity.badRequest()
                            .body(new MessageResponse(
                                    "Error: You have an approved first-half leave today. " +
                                    "Check-in is available from " + secondHalfStart.format(fmt) + "."));
                }
                initialStatus = "Half Day Present";
                isFirstHalfLeave = true;
                firstHalfLeaveReq = lr;
                break;
            }
            if (ShiftUtils.isSecondHalfLeave(lr, today)) {
                // SESSION_2 leave: check-in allowed only before second half starts (first-half work)
                if (!now.isBefore(secondHalfStart)) {
                    return ResponseEntity.badRequest()
                            .body(new MessageResponse(
                                    "Error: Your second-half leave is now active. Check-in is not allowed."));
                }
                initialStatus = "Half Day Present";
                break;
            }
        }

        Optional<AttendanceRecord> existingRecordOpt = attendanceRepository.findByUserAndDate(user, today);
        if (existingRecordOpt.isPresent()) {
            AttendanceRecord existing = existingRecordOpt.get();
            // Check if this existing record allows a second-half check-in:
            // Allowed if:
            // 1) First-half leave approved, time is at or after secondHalfStart, and record has no checkInTime
            // 2) OR first-half leave approved, time is at or after secondHalfStart, record was auto-checked out at secondHalfStart, and secondCheckInTime is null
            boolean canDoSecondHalfCheckIn = isFirstHalfLeave && !now.isBefore(secondHalfStart) &&
                    (existing.getCheckInTime() == null ||
                     (existing.getCheckInTime().isBefore(secondHalfStart) &&
                      (secondHalfStart.equals(existing.getCheckOutTime()) || existing.getFirstCheckOutTime() != null) &&
                      existing.getSecondCheckInTime() == null));

            if (!canDoSecondHalfCheckIn) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Error: You have already checked in today."));
            }

            // Perform second-half check-in on the existing record
            if (existing.getCheckInTime() == null) {
                // First check-in of the day
                existing.setCheckInTime(now);
                existing.setCheckOutTime(null);
            } else {
                // Preserve original check-in time (e.g. 12:49) from first half!
                if (existing.getFirstCheckOutTime() == null) {
                    existing.setFirstCheckOutTime(existing.getCheckOutTime() != null ? existing.getCheckOutTime() : secondHalfStart);
                }
                existing.setSecondCheckInTime(now);
                existing.setCheckOutTime(null); // Clear checkout so employee is actively working in 2nd half
            }

            existing.setStatus("Half Day Present");
            String lName = firstHalfLeaveReq != null ? firstHalfLeaveReq.getLeaveType().getName() : "Leave";
            existing.setLeaveNote("SESSION_1 " + lName + " (2nd half check-in at " + now.format(fmt) + ")");
            existing.setDeviceName(request.getDeviceName());
            existing.setLatitude(request.getLatitude());
            existing.setLongitude(request.getLongitude());
            attendanceRepository.save(existing);
            return ResponseEntity.ok(new MessageResponse("Checked in successfully for second half."));
        }

        AttendanceRecord record = new AttendanceRecord(user, today, now, initialStatus);
        record.setDeviceName(request.getDeviceName());
        record.setLatitude(request.getLatitude());
        record.setLongitude(request.getLongitude());
        attendanceRepository.save(record);

        return ResponseEntity.ok(new MessageResponse("Checked in successfully."));
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@RequestBody CheckInRequest request) {
        if (!isWithinGeofence(request.getLatitude(), request.getLongitude())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: You are outside the office geofence radius."));
        }

        User user = getCurrentUser();
        LocalDate today = LocalDate.now();

        AttendanceRecord record = attendanceRepository.findByUserAndDate(user, today)
                .orElseThrow(() -> new RuntimeException("Error: Check-in record not found for today."));

        if (record.getCheckOutTime() != null) {
            // The only exception: if checkout was auto-set to secondHalfStart and employee
            // wants to do a manual early checkout (before second half begins)
            LocalTime secondHalfStart = ShiftUtils.getSecondHalfStart(user, shiftConfig);
            LocalTime now = LocalTime.now();
            if (record.getCheckOutTime().equals(secondHalfStart) && now.isBefore(secondHalfStart)) {
                record.setCheckOutTime(now);
                attendanceRepository.save(record);
                return ResponseEntity.ok(new MessageResponse("Checked out successfully."));
            }
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: You have already checked out today."));
        }

        // Validate: checkout time must be >= active session check-in time
        LocalTime now = LocalTime.now();
        LocalTime effectiveInTime = record.getSecondCheckInTime() != null ? record.getSecondCheckInTime() : record.getCheckInTime();
        if (effectiveInTime != null && now.isBefore(effectiveInTime)) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Checkout time cannot be before check-in time."));
        }

        record.setCheckOutTime(now);
        attendanceRepository.save(record);

        return ResponseEntity.ok(new MessageResponse("Checked out successfully."));
    }

    @GetMapping("/records")
    public ResponseEntity<?> getAttendanceRecords() {
        User user = getCurrentUser();
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<AttendanceRecord> records = attendanceRepository.findByUserAndDateBetween(user, thirtyDaysAgo,
                LocalDate.now());
        return ResponseEntity.ok(records);
    }

    // Detailed day-wise attendance view — full records including device details
    @GetMapping("/detail")
    public ResponseEntity<?> getDetailedRecords(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        User user = getCurrentUser();
        LocalDate start, end;
        if (month != null && year != null) {
            YearMonth ym = YearMonth.of(year, month);
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        } else {
            end = LocalDate.now();
            start = end.withDayOfMonth(1);
        }
        List<AttendanceRecord> records = attendanceRepository.findByUserAndDateBetween(user, start, end);
        // Sort descending by date
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return ResponseEntity.ok(records);
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlyAttendanceDto>> getMonthlyAttendance(
            @RequestParam int month,
            @RequestParam int year) {
        User user = getCurrentUser();
        return ResponseEntity.ok(attendanceService.getMonthlyAttendance(user, year, month));
    }
}

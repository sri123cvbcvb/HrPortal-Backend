package com.hrms.backend.controller;

import com.hrms.backend.dto.CheckInRequest;
import com.hrms.backend.dto.MessageResponse;
import com.hrms.backend.dto.MonthlyAttendanceDto;
import com.hrms.backend.model.AttendanceRecord;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.AttendanceRepository;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.security.UserDetailsImpl;
import com.hrms.backend.service.AttendanceService;
import com.hrms.backend.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final GeoUtils geoUtils;
    private final AttendanceService attendanceService;

    @Value("${app.geofence.office.latitude}")
    private double officeLat;

    @Value("${app.geofence.office.longitude}")
    private double officeLon;

    @Value("${app.geofence.radius.meters}")
    private double geofenceRadius;

    public AttendanceController(AttendanceRepository attendanceRepository, UserRepository userRepository,
            GeoUtils geoUtils, AttendanceService attendanceService) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.geoUtils = geoUtils;
        this.attendanceService = attendanceService;
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

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest request) {
        if (!isWithinGeofence(request.getLatitude(), request.getLongitude())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: You are outside the office geofence radius."));
        }

        User user = getCurrentUser();
        LocalDate today = LocalDate.now();

        Optional<AttendanceRecord> existingRecord = attendanceRepository.findByUserAndDate(user, today);
        if (existingRecord.isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: You have already checked in today."));
        }

        AttendanceRecord record = new AttendanceRecord(user, today, LocalTime.now(), "Present");
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
            return ResponseEntity.badRequest().body(new MessageResponse("Error: You have already checked out today."));
        }

        record.setCheckOutTime(LocalTime.now());
        attendanceRepository.save(record);

        return ResponseEntity.ok(new MessageResponse("Checked out successfully."));
    }

    @GetMapping("/records")
    public ResponseEntity<?> getAttendanceRecords() {
        User user = getCurrentUser();
        // Return historical records for this user. A real app would paginate this.
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

package com.hrms.backend.controller;

import com.hrms.backend.dto.AttendanceSummaryDto;
import com.hrms.backend.model.CompanyLeave;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.CompanyLeaveRepository;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.security.UserDetailsImpl;
import com.hrms.backend.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/employee")
public class EmployeeController {

    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final CompanyLeaveRepository companyLeaveRepository;

    public EmployeeController(UserRepository userRepository, AttendanceService attendanceService,
            CompanyLeaveRepository companyLeaveRepository) {
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
        this.companyLeaveRepository = companyLeaveRepository;
    }

    private User getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Error: User is not found."));
    }

    @GetMapping("/attendance/summary")
    public ResponseEntity<?> getMyAttendanceSummary(
            @RequestParam int year,
            @RequestParam int month) {

        User user = getCurrentUser();
        AttendanceSummaryDto summary = attendanceService.getMonthlySummary(user, year, month);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/leaves/upcoming")
    public ResponseEntity<List<CompanyLeave>> getUpcomingLeaves() {
        LocalDate today = LocalDate.now();
        List<CompanyLeave> upcoming = companyLeaveRepository.findAll().stream()
                .filter(leave -> !leave.getDate().isBefore(today))
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(upcoming);
    }
}

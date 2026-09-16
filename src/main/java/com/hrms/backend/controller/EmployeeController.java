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

    @GetMapping("/profile")
    public ResponseEntity<com.hrms.backend.dto.EmployeeProfileDto> getMyProfile() {
        User user = getCurrentUser();
        List<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList())
                : List.of();

        com.hrms.backend.dto.EmployeeProfileDto dto = com.hrms.backend.dto.EmployeeProfileDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .dateOfJoining(user.getDateOfJoining())
                .dateOfExit(user.getDateOfExit())
                .aadhaarNumber(user.getAadhaarNumber())
                .panNumber(user.getPanNumber())
                .pfApplicable(user.getPfApplicable())
                .pfUan(user.getPfUan())
                .pfAccount(user.getPfAccount())
                .bankName(user.getBankName())
                .bankAccountNumber(user.getBankAccountNumber())
                .ifscCode(user.getIfscCode())
                .accountHolderName(user.getAccountHolderName())
                .annualCtc(user.getAnnualCtc())
                .basicPercentage(user.getBasicPercentage())
                .hraPercentage(user.getHraPercentage())
                .specialAllowancePercentage(user.getSpecialAllowancePercentage())
                .roles(roles)
                .build();

        return ResponseEntity.ok(dto);
    }
}

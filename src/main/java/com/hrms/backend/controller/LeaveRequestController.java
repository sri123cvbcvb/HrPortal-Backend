package com.hrms.backend.controller;

import com.hrms.backend.dto.LeaveRequestDto;
import com.hrms.backend.model.LeaveBalance;
import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.LeaveType;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee/leaves")
@PreAuthorize("hasRole('EMPLOYEE') or hasRole('ADMIN')")
@CrossOrigin(origins = "*", maxAge = 3600)
public class LeaveRequestController {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/types")
    public ResponseEntity<List<LeaveType>> getAvailableTypes() {
        return ResponseEntity.ok(leaveService.getAllLeaveTypes());
    }

    @GetMapping("/balances")
    public ResponseEntity<List<LeaveBalance>> getMyBalances(Authentication authentication) {
        User employee = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(leaveService.getBalancesForEmployee(employee));
    }

    @GetMapping("/history")
    public ResponseEntity<List<LeaveRequest>> getMyHistory(Authentication authentication) {
        User employee = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(leaveService.getEmployeeLeaveHistory(employee));
    }

    @PostMapping("/apply")
    public ResponseEntity<LeaveRequest> applyForLeave(@Valid @RequestBody LeaveRequestDto dto,
            Authentication authentication) {
        User employee = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        LeaveRequest request = leaveService.applyForLeave(employee, dto);
        return ResponseEntity.ok(request);
    }
}

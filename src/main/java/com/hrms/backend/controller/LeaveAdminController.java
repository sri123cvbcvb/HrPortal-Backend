package com.hrms.backend.controller;

import com.hrms.backend.model.LeaveBalance;
import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.LeaveType;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/leave-management")
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*", maxAge = 3600)
public class LeaveAdminController {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private UserRepository userRepository;

    // --- Types ---
    @GetMapping("/types")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        return ResponseEntity.ok(leaveService.getAllLeaveTypes());
    }

    @PostMapping("/types")
    public ResponseEntity<LeaveType> createLeaveType(@RequestBody LeaveType type) {
        return ResponseEntity.ok(leaveService.createLeaveType(type));
    }

    @GetMapping("/balances/all")
    public ResponseEntity<List<LeaveBalance>> getAllBalances() {
        return ResponseEntity.ok(leaveService.getAllBalances());
    }

    @GetMapping("/balances/{employeeId}")
    public ResponseEntity<List<LeaveBalance>> getEmployeeBalances(@PathVariable Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(leaveService.getBalancesForEmployee(employee));
    }

    @PostMapping("/balances/grant")
    public ResponseEntity<?> grantBalance(
            @RequestParam List<Long> employeeIds,
            @RequestParam Long leaveTypeId,
            @RequestParam double amount) {
        leaveService.grantLeaveBalance(employeeIds, leaveTypeId, amount);
        return ResponseEntity.ok("Balance granted successfully.");
    }

    @PutMapping("/balances/{balanceId}")
    public ResponseEntity<?> updateBalance(@PathVariable Long balanceId, @RequestParam double remainingDays) {
        LeaveBalance updated = leaveService.updateBalance(balanceId, remainingDays);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/balances/{balanceId}")
    public ResponseEntity<?> deleteBalance(@PathVariable Long balanceId) {
        leaveService.deleteBalance(balanceId);
        return ResponseEntity.ok("Balance deleted successfully.");
    }

    // --- Requests ---
    @GetMapping("/requests")
    public ResponseEntity<List<LeaveRequest>> getAllRequests() {
        return ResponseEntity.ok(leaveService.getAllLeaveRequests());
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<List<LeaveRequest>> getPendingRequests() {
        return ResponseEntity.ok(leaveService.getPendingRequests());
    }

    @PostMapping("/requests/{requestId}/approve")
    public ResponseEntity<LeaveRequest> approveRequest(@PathVariable Long requestId, Authentication authentication) {
        User admin = userRepository.findByUsername(authentication.getName()).orElse(null);
        return ResponseEntity.ok(leaveService.approveLeave(requestId, admin));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<LeaveRequest> rejectRequest(@PathVariable Long requestId, Authentication authentication) {
        User admin = userRepository.findByUsername(authentication.getName()).orElse(null);
        return ResponseEntity.ok(leaveService.rejectLeave(requestId, admin));
    }
}

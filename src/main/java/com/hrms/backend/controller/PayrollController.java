package com.hrms.backend.controller;

import com.hrms.backend.model.Payslip;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.security.UserDetailsImpl;
import com.hrms.backend.service.PayrollService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class PayrollController {

    private final PayrollService payrollService;
    private final UserRepository userRepository;

    public PayrollController(PayrollService payrollService, UserRepository userRepository) {
        this.payrollService = payrollService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Error: User not found."));
    }

    // --- Employee Endpoints ---

    @GetMapping("/employee/payroll/payslips")
    @PreAuthorize("hasRole('EMPLOYEE') or hasRole('ADMIN')")
    public ResponseEntity<List<Payslip>> getMyPayslips() {
        User user = getCurrentUser();
        List<Payslip> payslips = payrollService.getPayslipsForUser(user);
        return ResponseEntity.ok(payslips);
    }

    // --- Admin Endpoints ---

    /**
     * Manual trigger to generate payroll for a specific month and year.
     * Useful for initial testing or manual overrides before the 5th.
     */
    @PostMapping("/admin/payroll/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> generatePayrollManually(
            @RequestParam int month,
            @RequestParam int year) {
        
        payrollService.generatePayrollForMonth(month, year);
        return ResponseEntity.ok("Payroll generation process started for " + month + "/" + year);
    }
}

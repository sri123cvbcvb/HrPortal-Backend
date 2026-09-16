package com.hrms.backend.controller;

import com.hrms.backend.dto.MessageResponse;
import com.hrms.backend.model.Payslip;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.security.UserDetailsImpl;
import com.hrms.backend.service.PayrollService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @GetMapping("/employee/payroll/payslip")
    @PreAuthorize("hasRole('EMPLOYEE') or hasRole('ADMIN')")
    public ResponseEntity<?> getMonthlyPayslip(
            @RequestParam int month,
            @RequestParam int year) {
        User user = getCurrentUser();

        // 1. Check if already generated and saved
        Optional<Payslip> existing = payrollService.getPayslipForUserAndMonth(user, month, year);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }

        // 2. Disallow current or future (incomplete) months
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate targetMonthStart = LocalDate.of(year, month, 1);
        if (!targetMonthStart.isBefore(currentMonthStart)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Payslips cannot be generated for ongoing or future months (" + month + "/" + year + 
                    "). Monthly payroll is finalized after the month completes and generated automatically on the 5th."));
        }

        // 3. Validate Employment Dates
        LocalDate monthEnd = targetMonthStart.plusMonths(1).minusDays(1);
        if (user.getDateOfJoining() != null && user.getDateOfJoining().isAfter(monthEnd)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "You had not joined the company during " + month + "/" + year + 
                    " (Date of Joining: " + user.getDateOfJoining() + ")."));
        }

        if (user.getDateOfExit() != null && user.getDateOfExit().isBefore(targetMonthStart)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "You had already exited the company before " + month + "/" + year + 
                    " (Date of Exit: " + user.getDateOfExit() + ")."));
        }

        // 4. Auto-generate for past completed month if CTC is configured
        if (user.getAnnualCtc() != null && user.getAnnualCtc() > 0) {
            boolean created = payrollService.generatePayslipForEmployee(user, month, year);
            if (created) {
                Optional<Payslip> newlyCreated = payrollService.getPayslipForUserAndMonth(user, month, year);
                if (newlyCreated.isPresent()) {
                    return ResponseEntity.ok(newlyCreated.get());
                }
            }
        } else {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Salary structure (Annual CTC) is not configured for your profile. Please contact your HR administrator."));
        }

        return ResponseEntity.ok(new MessageResponse(
                "Payslip for " + month + "/" + year + " has not been generated yet. " +
                "Monthly payroll is automatically generated on the 5th of each month."));
    }

    // --- Admin Endpoints ---

    /**
     * Fetch all historical payslips for a given employee (Admin only).
     */
    @GetMapping("/admin/payroll/payslips")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getEmployeePayslipsForAdmin(@RequestParam Long userId) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Error: Employee not found."));
        List<Payslip> payslips = payrollService.getPayslipsForUser(employee);
        return ResponseEntity.ok(payslips);
    }

    /**
     * Fetch or generate monthly payslip for a specific employee (Admin only).
     */
    @GetMapping("/admin/payroll/payslip")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getEmployeeMonthlyPayslipForAdmin(
            @RequestParam Long userId,
            @RequestParam int month,
            @RequestParam int year) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Error: Employee not found."));

        // 1. Check if already generated
        Optional<Payslip> existing = payrollService.getPayslipForUserAndMonth(employee, month, year);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }

        // 2. Disallow current or future (incomplete) months
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate targetMonthStart = LocalDate.of(year, month, 1);
        if (!targetMonthStart.isBefore(currentMonthStart)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Payslips cannot be generated for ongoing or future months (" + month + "/" + year + 
                    "). Monthly payroll is finalized after the month completes and generated automatically on the 5th."));
        }

        // 3. Validate Employment Dates
        LocalDate monthEnd = targetMonthStart.plusMonths(1).minusDays(1);
        if (employee.getDateOfJoining() != null && employee.getDateOfJoining().isAfter(monthEnd)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Employee had not joined during " + month + "/" + year + 
                    " (Date of Joining: " + employee.getDateOfJoining() + ")."));
        }

        if (employee.getDateOfExit() != null && employee.getDateOfExit().isBefore(targetMonthStart)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Employee had already exited before " + month + "/" + year + 
                    " (Date of Exit: " + employee.getDateOfExit() + ")."));
        }

        // 4. Auto-generate for past completed month if CTC configured
        if (employee.getAnnualCtc() != null && employee.getAnnualCtc() > 0) {
            boolean created = payrollService.generatePayslipForEmployee(employee, month, year);
            if (created) {
                Optional<Payslip> newlyCreated = payrollService.getPayslipForUserAndMonth(employee, month, year);
                if (newlyCreated.isPresent()) {
                    return ResponseEntity.ok(newlyCreated.get());
                }
            }
        } else {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Annual CTC is not configured for this employee. Please set CTC in Employee Management before generating payslips."));
        }

        return ResponseEntity.ok(new MessageResponse(
                "Payslip for " + month + "/" + year + " could not be generated."));
    }

    /**
     * Streams PDF content back with native Content-Disposition attachment header
     * to ensure managed Chrome browsers download proper .pdf extension and filename.
     */
    @PostMapping(value = "/payroll/download-pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam("fileName") String fileName,
            @RequestParam("pdfData") String pdfData) {
        try {
            String cleanData = pdfData;
            if (cleanData.contains(",")) {
                cleanData = cleanData.split(",")[1];
            }
            byte[] pdfBytes = java.util.Base64.getDecoder().decode(cleanData);
            String safeName = (fileName != null && fileName.toLowerCase().endsWith(".pdf")) ? fileName : (fileName + ".pdf");
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

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
        return ResponseEntity.ok(new MessageResponse("Payroll generation process started for " + month + "/" + year));
    }
}

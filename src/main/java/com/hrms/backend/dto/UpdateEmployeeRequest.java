package com.hrms.backend.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateEmployeeRequest {

    private String firstName;
    private String lastName;

    @Email
    private String email;

    // Optional: new password (only updated if non-blank)
    private String password;

    // --- Statutory & Payroll Fields ---
    private String aadhaarNumber;
    private String panNumber;

    // PF Details
    private Boolean pfApplicable;
    private String pfUan;
    private String pfAccount;

    // Bank Details
    private String bankName;
    private String bankAccountNumber;
    private String ifscCode;
    private String accountHolderName;

    // Salary Structure
    private Double annualCtc;
    private Double basicPercentage;
    private Double hraPercentage;
    private Double specialAllowancePercentage;
}

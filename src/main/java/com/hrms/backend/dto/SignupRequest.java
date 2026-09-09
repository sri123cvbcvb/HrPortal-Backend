package com.hrms.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class SignupRequest {
    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    private Set<String> roles;

    @NotBlank
    private String password;

    // --- Statutory & Payroll Fields ---
    private String aadhaarNumber;
    private String panNumber;

    private Boolean pfApplicable;
    private String pfUan;
    private String pfAccount;

    private String bankName;
    private String bankAccountNumber;
    private String ifscCode;
    private String accountHolderName;

    private Double annualCtc;
    private Double basicPercentage;
    private Double hraPercentage;
    private Double specialAllowancePercentage;
}

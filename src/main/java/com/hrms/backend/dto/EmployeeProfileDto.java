package com.hrms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeProfileDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private LocalDate dateOfJoining;
    private LocalDate dateOfExit;

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

    private List<String> roles;
}

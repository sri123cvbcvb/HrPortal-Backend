package com.hrms.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;

    private String username;
    private String email;
    private String password;

    // --- Statutory & Payroll Fields ---
    
    // Employment Dates
    private LocalDate dateOfJoining;
    private LocalDate dateOfExit;

    // Personal Identification
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

    // Shift Timings (optional override per employee)
    private java.time.LocalTime shiftStartTime;
    private java.time.LocalTime shiftEndTime;
    private java.time.LocalTime secondHalfStartTime;

    public java.time.LocalTime getEffectiveSecondHalfStartTime(com.hrms.backend.config.ShiftConfig shiftConfig) {
        if (this.secondHalfStartTime != null) {
            return this.secondHalfStartTime;
        }
        return shiftConfig != null ? shiftConfig.getSecondHalfStartTime() : java.time.LocalTime.of(13, 0);
    }

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User(String firstName, String lastName, String username, String email, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.email = email;
        this.password = password;
    }
}

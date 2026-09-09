package com.hrms.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payslips")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payslip {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "roles", "aadhaarNumber", "panNumber", "pfUan", "pfAccount", 
        "bankName", "bankAccountNumber", "ifscCode", "accountHolderName", "hibernateLazyInitializer", "handler"})
    private User user;

    private Integer month; // 1-12
    private Integer year;

    private Double grossSalary;
    private Double totalDeductions;
    private Double netSalary;

    // Specific Deductions stored directly for easy reporting
    private Double pfDeduction;
    private Double incomeTax;
    private Double professionalTax;
    private Double lopDeduction;

    // Attendance stats for the generated month
    private Integer workingDays;
    private Integer presentDays;
    private Integer absentDays; // LOP days

    private LocalDate generatedDate;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayslipComponent> components = new ArrayList<>();

    public void addComponent(PayslipComponent component) {
        components.add(component);
        component.setPayslip(this);
    }
}

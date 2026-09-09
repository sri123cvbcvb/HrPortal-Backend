package com.hrms.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payslip_components")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayslipComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_id", nullable = false)
    private Payslip payslip;

    private String componentName;  // e.g., "Basic", "HRA", "PF", "Tax"
    private Double amount;
    
    @Enumerated(EnumType.STRING)
    private ComponentType type;

    public PayslipComponent(String componentName, Double amount, ComponentType type) {
        this.componentName = componentName;
        this.amount = amount;
        this.type = type;
    }

    public enum ComponentType {
        EARNING,
        DEDUCTION
    }
}

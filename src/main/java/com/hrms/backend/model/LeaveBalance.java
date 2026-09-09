package com.hrms.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_balances")
@Data
@NoArgsConstructor
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false, columnDefinition = "double default 0")
    private double totalGranted;

    @Column(nullable = false, columnDefinition = "double default 0")
    private double usedDays;

    @Column(nullable = false)
    private double remainingDays;

    public LeaveBalance(User employee, LeaveType leaveType, double remainingDays) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.totalGranted = 0;
        this.usedDays = 0;
        this.remainingDays = remainingDays;
    }
}

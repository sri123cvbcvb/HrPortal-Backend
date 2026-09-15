package com.hrms.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit trail for attendance record mutations caused by leave approvals, rejections, or cancellations.
 * Enables reverting attendance when leave is rejected or cancelled.
 */
@Entity
@Table(name = "attendance_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAuditLog {

    public enum AuditAction {
        LEAVE_APPROVED, LEAVE_REJECTED, LEAVE_CANCELLED, LEAVE_APPROVAL_REVERTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The employee whose attendance was affected. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The date of the attendance record that was mutated. */
    @Column(nullable = false)
    private LocalDate attendanceDate;

    /** The leave request that caused this mutation. */
    @Column(nullable = false)
    private Long leaveRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    /** Status of the attendance record BEFORE the mutation (null if no record existed). */
    private String previousStatus;

    /** Checkout time BEFORE the mutation (null if no record existed or no checkout). */
    private java.time.LocalTime previousCheckOutTime;

    /** Status of the attendance record AFTER the mutation. */
    private String newStatus;

    /** Checkout time AFTER the mutation. */
    private java.time.LocalTime newCheckOutTime;

    /** Whether a new attendance record was created (vs an existing one being updated). */
    @Column(nullable = false)
    private boolean attendanceRecordCreated = false;

    /** Who triggered the action (admin/employee username). */
    private String mutatedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime mutatedAt;

    @PrePersist
    protected void onCreate() {
        this.mutatedAt = LocalDateTime.now();
    }
}

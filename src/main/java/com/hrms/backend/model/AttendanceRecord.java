package com.hrms.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDate date;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private String status; // e.g., "Present", "Absent", "Half Day Present", "PL", "SL", "CL"

    // Device / Location details captured at check-in
    private String deviceName;
    private Double latitude;
    private Double longitude;

    // Multi-session tracking (e.g. 1st half attendance + 1st half leave auto-checkout + 2nd half attendance)
    private LocalTime firstCheckOutTime;
    private LocalTime secondCheckInTime;

    /**
     * Human-readable note explaining why the attendance record was mutated by a leave action.
     * E.g. "SESSION_2 leave auto-checkout", "Full-day PL retroactive", "SESSION_1 leave".
     * Null when this is a normal check-in/check-out record.
     */
    private String leaveNote;

    // Convenience constructor used when creating a fresh check-in record
    public AttendanceRecord(User user, LocalDate date, LocalTime checkInTime, String status) {
        this.user = user;
        this.date = date;
        this.checkInTime = checkInTime;
        this.status = status;
    }
}


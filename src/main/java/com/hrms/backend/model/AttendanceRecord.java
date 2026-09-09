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
    private String status; // e.g., "Present", "Absent"

    // Device / Location details captured at check-in
    private String deviceName;
    private Double latitude;
    private Double longitude;

    // Convenience constructor used when creating a fresh check-in record
    public AttendanceRecord(User user, LocalDate date, LocalTime checkInTime, String status) {
        this.user = user;
        this.date = date;
        this.checkInTime = checkInTime;
        this.status = status;
    }
}

package com.hrms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDto {
    private LocalDate month;
    private int totalDays;
    private int presentDays;
    private int absentDays;
    private double attendancePercentage;
}

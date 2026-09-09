package com.hrms.backend.dto;

import java.time.LocalDate;

public class MonthlyAttendanceDto {
    private LocalDate date;
    private String status; // P, A, SL, CL, PL, H, O
    private String shift;
    private String signInTime;
    private String signOutTime;

    public MonthlyAttendanceDto() {
    }

    public MonthlyAttendanceDto(LocalDate date, String status, String shift, String signInTime, String signOutTime) {
        this.date = date;
        this.status = status;
        this.shift = shift;
        this.signInTime = signInTime;
        this.signOutTime = signOutTime;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getSignInTime() {
        return signInTime;
    }

    public void setSignInTime(String signInTime) {
        this.signInTime = signInTime;
    }

    public String getSignOutTime() {
        return signOutTime;
    }

    public void setSignOutTime(String signOutTime) {
        this.signOutTime = signOutTime;
    }
}

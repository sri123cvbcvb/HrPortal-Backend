package com.hrms.backend.dto;

import java.time.LocalDate;

public class MonthlyAttendanceDto {
    private LocalDate date;
    private String status; // P, A, SL, CL, PL, H, O
    private String shift;
    private String signInTime;
    private String signOutTime;
    private String leaveSession; // "SESSION_1", "SESSION_2", "FULL_DAY", or null
    private String leaveType;    // "SL", "CL", "PL", or null
    private String leaveNote;    // Human-readable leave note

    public MonthlyAttendanceDto() {
    }

    public MonthlyAttendanceDto(LocalDate date, String status, String shift, String signInTime, String signOutTime) {
        this.date = date;
        this.status = status;
        this.shift = shift;
        this.signInTime = signInTime;
        this.signOutTime = signOutTime;
    }

    public MonthlyAttendanceDto(LocalDate date, String status, String shift, String signInTime, String signOutTime,
                                String leaveSession, String leaveType, String leaveNote) {
        this.date = date;
        this.status = status;
        this.shift = shift;
        this.signInTime = signInTime;
        this.signOutTime = signOutTime;
        this.leaveSession = leaveSession;
        this.leaveType = leaveType;
        this.leaveNote = leaveNote;
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

    public String getLeaveSession() {
        return leaveSession;
    }

    public void setLeaveSession(String leaveSession) {
        this.leaveSession = leaveSession;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public String getLeaveNote() {
        return leaveNote;
    }

    public void setLeaveNote(String leaveNote) {
        this.leaveNote = leaveNote;
    }
}

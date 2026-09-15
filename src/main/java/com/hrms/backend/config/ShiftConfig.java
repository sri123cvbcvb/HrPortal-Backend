package com.hrms.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class ShiftConfig {

    @Value("${app.shift.name:General Shift}")
    private String name;

    @Value("${app.shift.start-time:10:00}")
    private String startTimeStr;

    @Value("${app.shift.end-time:18:00}")
    private String endTimeStr;

    @Value("${app.shift.second-half-start-time:13:00}")
    private String secondHalfStartTimeStr;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalTime getStartTime() {
        return LocalTime.parse(startTimeStr, TIME_FORMATTER);
    }

    public void setStartTimeStr(String startTimeStr) {
        this.startTimeStr = startTimeStr;
    }

    public LocalTime getEndTime() {
        return LocalTime.parse(endTimeStr, TIME_FORMATTER);
    }

    public void setEndTimeStr(String endTimeStr) {
        this.endTimeStr = endTimeStr;
    }

    public LocalTime getSecondHalfStartTime() {
        return LocalTime.parse(secondHalfStartTimeStr, TIME_FORMATTER);
    }

    public void setSecondHalfStartTimeStr(String secondHalfStartTimeStr) {
        this.secondHalfStartTimeStr = secondHalfStartTimeStr;
    }
}

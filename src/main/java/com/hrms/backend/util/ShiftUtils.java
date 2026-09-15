package com.hrms.backend.util;

import com.hrms.backend.config.ShiftConfig;
import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.User;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Centralized utility for all shift-boundary calculations.
 * All half-day boundary comparisons use: < secondHalfStart = first half, >= secondHalfStart = second half.
 */
public final class ShiftUtils {

    private ShiftUtils() {}

    /** Resolves the effective shift start time for a given user (falls back to global config). */
    public static LocalTime getShiftStart(User user, ShiftConfig cfg) {
        if (user != null && user.getShiftStartTime() != null) {
            return user.getShiftStartTime();
        }
        return cfg != null ? cfg.getStartTime() : LocalTime.of(10, 0);
    }

    /** Resolves the effective shift end time for a given user (falls back to global config). */
    public static LocalTime getShiftEnd(User user, ShiftConfig cfg) {
        if (user != null && user.getShiftEndTime() != null) {
            return user.getShiftEndTime();
        }
        return cfg != null ? cfg.getEndTime() : LocalTime.of(18, 0);
    }

    /**
     * Resolves the effective second-half start time (= first-half end time).
     * Boundary rule: < secondHalfStart => first half; >= secondHalfStart => second half.
     */
    public static LocalTime getSecondHalfStart(User user, ShiftConfig cfg) {
        if (user != null && user.getSecondHalfStartTime() != null) {
            return user.getSecondHalfStartTime();
        }
        return cfg != null ? cfg.getSecondHalfStartTime() : LocalTime.of(13, 0);
    }

    /** Returns true if the given leave covers the second half on the given date. */
    public static boolean isSecondHalfLeave(LeaveRequest lr, LocalDate date) {
        if (lr.getFromDate().equals(lr.getToDate())) {
            return lr.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_2;
        }
        if (date.equals(lr.getFromDate())) {
            return lr.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_2;
        }
        if (date.equals(lr.getToDate())) {
            return lr.getSessionTo() == LeaveRequest.LeaveSession.SESSION_2;
        }
        // Middle days of multi-day leave are always full day
        return false;
    }

    /** Returns true if the given leave covers the first half on the given date. */
    public static boolean isFirstHalfLeave(LeaveRequest lr, LocalDate date) {
        if (lr.getFromDate().equals(lr.getToDate())) {
            return lr.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_1;
        }
        if (date.equals(lr.getFromDate())) {
            return lr.getSessionFrom() == LeaveRequest.LeaveSession.SESSION_1;
        }
        if (date.equals(lr.getToDate())) {
            return lr.getSessionTo() == LeaveRequest.LeaveSession.SESSION_1;
        }
        return false;
    }

    /** Returns true if the given leave is a full-day leave on the given date. */
    public static boolean isFullDayLeave(LeaveRequest lr, LocalDate date) {
        if (lr.getFromDate().equals(lr.getToDate())) {
            return lr.getSessionFrom() == LeaveRequest.LeaveSession.FULL_DAY;
        }
        // Middle days of multi-day spans are full day
        return !date.equals(lr.getFromDate()) && !date.equals(lr.getToDate());
    }

    /**
     * Returns true if the given check-in time falls in the second half.
     * Boundary: checkIn >= secondHalfStart => second half.
     */
    public static boolean isCheckedInDuringSecondHalf(LocalTime checkInTime, LocalTime secondHalfStart) {
        return checkInTime != null && !checkInTime.isBefore(secondHalfStart);
    }
}

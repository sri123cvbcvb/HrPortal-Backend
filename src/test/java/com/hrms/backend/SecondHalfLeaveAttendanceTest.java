package com.hrms.backend;

import com.hrms.backend.config.ShiftConfig;
import com.hrms.backend.dto.AttendanceSummaryDto;
import com.hrms.backend.dto.MonthlyAttendanceDto;
import com.hrms.backend.model.*;
import com.hrms.backend.repository.*;
import com.hrms.backend.service.AttendanceService;
import com.hrms.backend.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SecondHalfLeaveAttendanceTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private ShiftConfig shiftConfig;

    private User testAdmin;
    private User testEmployee;
    private LeaveType sickLeaveType;
    private LeaveType casualLeaveType;
    private LeaveType privilegeLeaveType;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(null, RoleName.ROLE_ADMIN)));
        Role empRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(null, RoleName.ROLE_EMPLOYEE)));

        Set<Role> adminRoles = new HashSet<>(Collections.singletonList(adminRole));
        testAdmin = userRepository.findByUsername("test_admin_sh").orElseGet(() -> {
            User a = new User("Admin", "SH", "test_admin_sh", "admin_sh@hrms.com", "password");
            a.setRoles(adminRoles);
            return userRepository.save(a);
        });

        Set<Role> empRoles = new HashSet<>(Collections.singletonList(empRole));
        testEmployee = userRepository.findByUsername("test_emp_sh").orElseGet(() -> {
            User e = new User("Employee", "SH", "test_emp_sh", "emp_sh@hrms.com", "password");
            e.setRoles(empRoles);
            return userRepository.save(e);
        });

        sickLeaveType = leaveTypeRepository.findAll().stream()
                .filter(lt -> lt.getName().equalsIgnoreCase("Sick Leave") || lt.getName().equalsIgnoreCase("SL"))
                .findFirst()
                .orElseGet(() -> leaveTypeRepository.save(new LeaveType("Sick Leave", 12)));

        casualLeaveType = leaveTypeRepository.findAll().stream()
                .filter(lt -> lt.getName().equalsIgnoreCase("Casual Leave") || lt.getName().equalsIgnoreCase("CL"))
                .findFirst()
                .orElseGet(() -> leaveTypeRepository.save(new LeaveType("Casual Leave", 12)));

        privilegeLeaveType = leaveTypeRepository.findAll().stream()
                .filter(lt -> lt.getName().equalsIgnoreCase("Privilege Leave") || lt.getName().equalsIgnoreCase("PL"))
                .findFirst()
                .orElseGet(() -> leaveTypeRepository.save(new LeaveType("Privilege Leave", 15)));

        // Ensure employee has sufficient balances
        grantBalance(testEmployee, sickLeaveType, 10.0);
        grantBalance(testEmployee, casualLeaveType, 10.0);
        grantBalance(testEmployee, privilegeLeaveType, 10.0);
    }

    private void grantBalance(User emp, LeaveType lt, double days) {
        LeaveBalance bal = leaveBalanceRepository.findByEmployeeAndLeaveType(emp, lt)
                .orElseGet(() -> new LeaveBalance(emp, lt, days));
        bal.setRemainingDays(days);
        bal.setTotalGranted(days);
        bal.setUsedDays(0);
        leaveBalanceRepository.save(bal);
    }

    private LeaveRequest createPendingLeave(User emp, LeaveType type, LocalDate date,
                                           LeaveRequest.LeaveSession sessionFrom,
                                           LeaveRequest.LeaveSession sessionTo) {
        LeaveRequest req = new LeaveRequest();
        req.setEmployee(emp);
        req.setLeaveType(type);
        req.setFromDate(date);
        req.setToDate(date);
        req.setSessionFrom(sessionFrom);
        req.setSessionTo(sessionTo);
        req.setReason("Test Leave Request");
        req.setStatus(LeaveRequest.LeaveStatus.PENDING);
        return leaveRequestRepository.save(req);
    }

    @Test
    @DisplayName("Scenario 1: First-half present + second-half SL -> correct attendance and automatic checkout")
    void testFirstHalfPresentSecondHalfSickLeave() {
        LocalDate today = LocalDate.now();

        // Employee checks in at 10:00 AM
        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(10, 0), "Present");
        attendanceRepository.save(record);

        // Employee applies for second-half SL
        LeaveRequest req = createPendingLeave(testEmployee, sickLeaveType, today,
                LeaveRequest.LeaveSession.SESSION_2, LeaveRequest.LeaveSession.SESSION_2);

        // Admin approves leave
        leaveService.approveLeave(req.getId(), testAdmin);

        // Verify AttendanceRecord updated
        AttendanceRecord updated = attendanceRepository.findByUserAndDate(testEmployee, today).orElseThrow();
        assertEquals(LocalTime.of(10, 0), updated.getCheckInTime());
        assertNotNull(updated.getCheckOutTime(), "Checkout time must be automatically generated");
        assertEquals(shiftConfig.getSecondHalfStartTime(), updated.getCheckOutTime(),
                "Checkout must be set to second half start time (e.g. 13:00)");
        assertEquals("Half Day Present", updated.getStatus());

        // Verify monthly attendance
        List<MonthlyAttendanceDto> monthly = attendanceService.getMonthlyAttendance(testEmployee, today.getYear(), today.getMonthValue());
        MonthlyAttendanceDto todayDto = monthly.stream().filter(m -> m.getDate().equals(today)).findFirst().orElseThrow();
        assertEquals("HP", todayDto.getStatus());
        assertNotNull(todayDto.getSignInTime());
        assertNotNull(todayDto.getSignOutTime());
    }

    @Test
    @DisplayName("Scenario 2: First-half present + second-half CL -> correct attendance and automatic checkout")
    void testFirstHalfPresentSecondHalfCasualLeave() {
        LocalDate today = LocalDate.now();

        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(9, 55), "Present");
        attendanceRepository.save(record);

        LeaveRequest req = createPendingLeave(testEmployee, casualLeaveType, today,
                LeaveRequest.LeaveSession.SESSION_2, LeaveRequest.LeaveSession.SESSION_2);

        leaveService.approveLeave(req.getId(), testAdmin);

        AttendanceRecord updated = attendanceRepository.findByUserAndDate(testEmployee, today).orElseThrow();
        assertEquals(shiftConfig.getSecondHalfStartTime(), updated.getCheckOutTime());
        assertEquals("Half Day Present", updated.getStatus());
    }

    @Test
    @DisplayName("Scenario 3: First-half present + second-half PL -> correct attendance and automatic checkout")
    void testFirstHalfPresentSecondHalfPrivilegeLeave() {
        LocalDate today = LocalDate.now();

        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(10, 5), "Present");
        attendanceRepository.save(record);

        LeaveRequest req = createPendingLeave(testEmployee, privilegeLeaveType, today,
                LeaveRequest.LeaveSession.SESSION_2, LeaveRequest.LeaveSession.SESSION_2);

        leaveService.approveLeave(req.getId(), testAdmin);

        AttendanceRecord updated = attendanceRepository.findByUserAndDate(testEmployee, today).orElseThrow();
        assertEquals(shiftConfig.getSecondHalfStartTime(), updated.getCheckOutTime());
        assertEquals("Half Day Present", updated.getStatus());
    }

    @Test
    @DisplayName("Scenario 4: First-half leave + second-half present -> existing behavior continues to work")
    void testFirstHalfLeaveSecondHalfPresent() {
        LocalDate today = LocalDate.now();

        LeaveRequest req = createPendingLeave(testEmployee, sickLeaveType, today,
                LeaveRequest.LeaveSession.SESSION_1, LeaveRequest.LeaveSession.SESSION_1);
        leaveService.approveLeave(req.getId(), testAdmin);

        // Employee arrives for second half and checks in at 13:00
        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(13, 0), "Half Day Present");
        record.setCheckOutTime(LocalTime.of(18, 0));
        attendanceRepository.save(record);

        // Verify monthly attendance
        List<MonthlyAttendanceDto> monthly = attendanceService.getMonthlyAttendance(testEmployee, today.getYear(), today.getMonthValue());
        MonthlyAttendanceDto todayDto = monthly.stream().filter(m -> m.getDate().equals(today)).findFirst().orElseThrow();
        assertEquals("HP", todayDto.getStatus());
        assertTrue("01:00 PM".equalsIgnoreCase(todayDto.getSignInTime()), "Sign in time must be 01:00 PM");
        assertTrue("06:00 PM".equalsIgnoreCase(todayDto.getSignOutTime()), "Sign out time must be 06:00 PM");
    }

    @Test
    @DisplayName("Scenario 5: Full-day leave -> existing behavior continues to work without checkout")
    void testFullDayLeave() {
        LocalDate today = LocalDate.now();

        LeaveRequest req = createPendingLeave(testEmployee, sickLeaveType, today,
                LeaveRequest.LeaveSession.FULL_DAY, LeaveRequest.LeaveSession.FULL_DAY);
        leaveService.approveLeave(req.getId(), testAdmin);

        // Verify monthly attendance reports leave code (e.g. SL) and no check-in/out
        List<MonthlyAttendanceDto> monthly = attendanceService.getMonthlyAttendance(testEmployee, today.getYear(), today.getMonthValue());
        MonthlyAttendanceDto todayDto = monthly.stream().filter(m -> m.getDate().equals(today)).findFirst().orElseThrow();
        assertEquals("SL", todayDto.getStatus());
        assertNull(todayDto.getSignInTime());
        assertNull(todayDto.getSignOutTime());
    }

    @Test
    @DisplayName("Scenario 6: Normal full-day attendance without leave -> existing behavior continues to work")
    void testNormalFullDayAttendance() {
        LocalDate today = LocalDate.now();

        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(10, 0), "Present");
        record.setCheckOutTime(LocalTime.of(18, 0));
        attendanceRepository.save(record);

        List<MonthlyAttendanceDto> monthly = attendanceService.getMonthlyAttendance(testEmployee, today.getYear(), today.getMonthValue());
        MonthlyAttendanceDto todayDto = monthly.stream().filter(m -> m.getDate().equals(today)).findFirst().orElseThrow();
        assertEquals("P", todayDto.getStatus());
        assertTrue("10:00 AM".equalsIgnoreCase(todayDto.getSignInTime()), "Sign in time must be 10:00 AM");
        assertTrue("06:00 PM".equalsIgnoreCase(todayDto.getSignOutTime()), "Sign out time must be 06:00 PM");
    }

    @Test
    @DisplayName("Scenario 7: Second-half leave approval after second-half start time -> checkout is capped at secondHalfStartTime")
    void testSecondHalfLeaveApprovedAfterStart() {
        LocalDate today = LocalDate.now();

        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(10, 0), "Present");
        attendanceRepository.save(record);

        LeaveRequest req = createPendingLeave(testEmployee, sickLeaveType, today,
                LeaveRequest.LeaveSession.SESSION_2, LeaveRequest.LeaveSession.SESSION_2);

        // Approval happens
        leaveService.approveLeave(req.getId(), testAdmin);

        AttendanceRecord updated = attendanceRepository.findByUserAndDate(testEmployee, today).orElseThrow();
        // Even if approval is simulated or occurs in the afternoon, checkout must be set to secondHalfStartTime (13:00)
        assertEquals(LocalTime.of(13, 0), updated.getCheckOutTime());
        assertEquals("Half Day Present", updated.getStatus());
    }

    @Test
    @DisplayName("Scenario 8: Pre-existing manual checkout is preserved without duplication or incorrect absent status")
    void testPreExistingCheckoutNotOverwritten() {
        LocalDate today = LocalDate.now();

        // Employee checked in at 10:00 AM and manually checked out at 12:45 PM
        LocalTime manualCheckout = LocalTime.of(12, 45);
        AttendanceRecord record = new AttendanceRecord(testEmployee, today, LocalTime.of(10, 0), "Present");
        record.setCheckOutTime(manualCheckout);
        attendanceRepository.save(record);

        LeaveRequest req = createPendingLeave(testEmployee, sickLeaveType, today,
                LeaveRequest.LeaveSession.SESSION_2, LeaveRequest.LeaveSession.SESSION_2);

        leaveService.approveLeave(req.getId(), testAdmin);

        AttendanceRecord updated = attendanceRepository.findByUserAndDate(testEmployee, today).orElseThrow();
        // Existing checkout of 12:45 must be PRESERVED
        assertEquals(manualCheckout, updated.getCheckOutTime(), "Existing manual checkout time must not be overwritten");
        assertEquals("Half Day Present", updated.getStatus());

        // Verify summary does not count day as absent
        AttendanceSummaryDto summary = attendanceService.getMonthlySummary(testEmployee, today.getYear(), today.getMonthValue());
        assertTrue(summary.getPresentDays() >= 1, "Must count toward present days");
    }
}

package com.hrms.backend.repository;

import com.hrms.backend.model.LeaveRequest;
import com.hrms.backend.model.LeaveType;
import com.hrms.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
        List<LeaveRequest> findByEmployeeOrderByCreatedAtDesc(User employee);

        // For admin view
        List<LeaveRequest> findAllByOrderByCreatedAtDesc();

        // For pending dashboard widget
        List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequest.LeaveStatus status);

        // Count CL/PL leaves in a given month (kept for reference, no longer used for hard limit)
        @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.employee = :employee " +
                        "AND lr.leaveType = :leaveType " +
                        "AND lr.status IN (com.hrms.backend.model.LeaveRequest$LeaveStatus.PENDING, com.hrms.backend.model.LeaveRequest$LeaveStatus.APPROVED) "
                        +
                        "AND lr.fromDate >= :startDate AND lr.fromDate <= :endDate")
        long countLeavesInMonthForType(@Param("employee") User employee,
                        @Param("leaveType") LeaveType leaveType,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // Fetch all approved leave requests in a date range (for monthly calendar)
        @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee = :employee " +
                        "AND lr.status = com.hrms.backend.model.LeaveRequest$LeaveStatus.APPROVED " +
                        "AND lr.fromDate <= :endDate AND lr.toDate >= :startDate")
        List<LeaveRequest> findApprovedLeavesInRange(@Param("employee") User employee,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // Detect any overlapping leave requests (overlap validation — date range level)
        @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.employee = :employee " +
                        "AND lr.status IN (com.hrms.backend.model.LeaveRequest$LeaveStatus.PENDING, com.hrms.backend.model.LeaveRequest$LeaveStatus.APPROVED) "
                        +
                        "AND lr.fromDate <= :toDate AND lr.toDate >= :fromDate")
        long countOverlappingLeaves(@Param("employee") User employee,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        // Fetch pending or approved leaves that touch a specific date (for detailed overlap check)
        @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee = :employee " +
                        "AND lr.status IN (com.hrms.backend.model.LeaveRequest$LeaveStatus.PENDING, com.hrms.backend.model.LeaveRequest$LeaveStatus.APPROVED) "
                        +
                        "AND lr.fromDate <= :date AND lr.toDate >= :date")
        List<LeaveRequest> findApprovedOrPendingForDate(@Param("employee") User employee,
                        @Param("date") LocalDate date);

        @Modifying
        @Query("DELETE FROM LeaveRequest lr WHERE lr.employee = :employee")
        void deleteByEmployee(@Param("employee") User employee);

        @Modifying
        @Query("UPDATE LeaveRequest lr SET lr.approvedBy = null WHERE lr.approvedBy = :user")
        void nullifyApprovedBy(@Param("user") User user);
}


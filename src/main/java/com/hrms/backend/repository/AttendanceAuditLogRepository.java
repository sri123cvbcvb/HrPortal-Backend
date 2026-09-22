package com.hrms.backend.repository;

import com.hrms.backend.model.AttendanceAuditLog;
import com.hrms.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceAuditLogRepository extends JpaRepository<AttendanceAuditLog, Long> {

    /** Find all audit entries for a specific leave request (used for reverting on cancel/reject). */
    List<AttendanceAuditLog> findByLeaveRequestIdOrderByMutatedAtDesc(Long leaveRequestId);

    /** Find all audit entries for a user on a specific date. */
    List<AttendanceAuditLog> findByUserAndAttendanceDateOrderByMutatedAtDesc(User user, LocalDate date);

    @Modifying
    @Query("DELETE FROM AttendanceAuditLog aal WHERE aal.user = :user")
    void deleteByUser(@Param("user") User user);
}


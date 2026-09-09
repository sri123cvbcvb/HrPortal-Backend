package com.hrms.backend.repository;

import com.hrms.backend.model.AttendanceRecord;
import com.hrms.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByUserAndDate(User user, LocalDate date);
    List<AttendanceRecord> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);
}

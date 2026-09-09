package com.hrms.backend.repository;

import com.hrms.backend.model.CompanyLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompanyLeaveRepository extends JpaRepository<CompanyLeave, Long> {
    List<CompanyLeave> findByDateBetween(LocalDate startDate, LocalDate endDate);
}

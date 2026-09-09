package com.hrms.backend.repository;

import com.hrms.backend.model.Payslip;
import com.hrms.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByUserOrderByYearDescMonthDesc(User user);
    Optional<Payslip> findByUserAndMonthAndYear(User user, int month, int year);
    boolean existsByUserAndMonthAndYear(User user, int month, int year);
}

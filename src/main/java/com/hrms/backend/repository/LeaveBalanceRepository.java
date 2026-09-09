package com.hrms.backend.repository;

import com.hrms.backend.model.LeaveBalance;
import com.hrms.backend.model.LeaveType;
import com.hrms.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findByEmployee(User employee);

    Optional<LeaveBalance> findByEmployeeAndLeaveType(User employee, LeaveType leaveType);
}

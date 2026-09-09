package com.hrms.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PayrollScheduler {
    private static final Logger logger = LoggerFactory.getLogger(PayrollScheduler.class);

    private final PayrollService payrollService;

    public PayrollScheduler(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    /**
     * Automatic Scheduler running on the 5th of every month at 2:00 AM.
     * Generates payslips for the PREVIOUS month.
     * Cron expression: "0 0 2 5 * ?" -> Sec:0 Min:0 Hr:2 Day:5 Month:Any
     * DayOfWeek:Any
     */
    @Scheduled(cron = "0 0 2 5 * ?")
    public void scheduleMonthlyPayroll() {
        LocalDate today = LocalDate.now();
        // Since it runs on the 5th, the target month for payroll is the previous month
        LocalDate previousMonthDate = today.minusMonths(1);

        int targetMonth = previousMonthDate.getMonthValue();
        int targetYear = previousMonthDate.getYear();

        logger.info("Executing automatic monthly payroll scheduler for {}/{}", targetMonth, targetYear);
        payrollService.generatePayrollForMonth(targetMonth, targetYear);
    }
}

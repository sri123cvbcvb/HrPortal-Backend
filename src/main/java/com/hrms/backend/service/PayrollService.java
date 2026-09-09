package com.hrms.backend.service;

import com.hrms.backend.dto.AttendanceSummaryDto;
import com.hrms.backend.model.Payslip;
import com.hrms.backend.model.PayslipComponent;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.PayslipRepository;
import com.hrms.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PayrollService {
    private static final Logger logger = LoggerFactory.getLogger(PayrollService.class);

    private final PayslipRepository payslipRepository;
    private final UserRepository userRepository;
    private final AttendanceService attendanceService;

    public PayrollService(PayslipRepository payslipRepository, UserRepository userRepository, AttendanceService attendanceService) {
        this.payslipRepository = payslipRepository;
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
    }

    /**
     * Generates payslips for all active employees who have a defined CTC
     * for a given month/year. Used by the automated scheduler.
     */
    @Transactional
    public void generatePayrollForMonth(int month, int year) {
        logger.info("Starting automated payroll generation for {}/{}", month, year);
        List<User> employees = userRepository.findAll();
        int count = 0;

        for (User employee : employees) {
            // Only generate if employee has a CTC defined
            if (employee.getAnnualCtc() != null && employee.getAnnualCtc() > 0) {
                try {
                    boolean generated = generatePayslipForEmployee(employee, month, year);
                    if (generated) count++;
                } catch (Exception e) {
                    logger.error("Failed to generate payslip for user {}: {}", employee.getUsername(), e.getMessage());
                }
            }
        }
        logger.info("Completed payroll generation. Generated {} payslips.", count);
    }

    /**
     * Calculates and saves a payslip for a single employee.
     * @return true if generated, false if skipped (e.g. already exists)
     */
    @Transactional
    public boolean generatePayslipForEmployee(User employee, int month, int year) {
        // Prevent duplicate generation
        if (payslipRepository.existsByUserAndMonthAndYear(employee, month, year)) {
            logger.debug("Payslip already exists for user {} for {}/{}", employee.getUsername(), month, year);
            return false;
        }

        // 1. Fetch Attendance Summary to get working days and LOP days
        AttendanceSummaryDto attendance = attendanceService.getMonthlySummary(employee, year, month);
        int totalWorkingDays = attendance.getTotalDays();
        int absentDays = attendance.getAbsentDays(); // LOP days

        // 2. Base Salary Calculations
        double annualCtc = employee.getAnnualCtc();
        double monthlyCtc = annualCtc / 12.0;

        double basicPercent = employee.getBasicPercentage() != null ? employee.getBasicPercentage() : 40.0;
        double hraPercent = employee.getHraPercentage() != null ? employee.getHraPercentage() : 20.0;
        double specialPercent = employee.getSpecialAllowancePercentage() != null ? employee.getSpecialAllowancePercentage() : (100.0 - basicPercent - hraPercent);

        double monthlyBasic = (monthlyCtc * basicPercent) / 100.0;
        double monthlyHra = (monthlyCtc * hraPercent) / 100.0;
        double monthlySpecial = (monthlyCtc * specialPercent) / 100.0;
        
        // Sum components to get Gross (before any deductions based on attendance)
        double grossSalary = monthlyBasic + monthlyHra + monthlySpecial;

        // 3. Loss of Pay (LOP) Deduction
        double lopDeduction = 0.0;
        if (totalWorkingDays > 0 && absentDays > 0) {
            double perDaySalary = grossSalary / totalWorkingDays;
            lopDeduction = perDaySalary * absentDays;
        }

        // Available gross after LOP (used to calculate taxes if needed, but PF is usually on standard basic)
        // For simplicity, we'll calculate statutory deductions on standard monthly values, 
        // though strictly PF might be pro-rated based on LOP in some companies.

        // 4. Statutory Deductions
        double pfDeduction = 0.0;
        if (Boolean.TRUE.equals(employee.getPfApplicable())) {
            // PF is 12% of Basic, max cap usually applies (e.g. 12% of 15000 = 1800 max)
            double calculatedPf = monthlyBasic * 0.12;
            pfDeduction = Math.min(calculatedPf, 1800.0);
        }

        double ptDeduction = 0.0;
        if (grossSalary > 21000) {
            ptDeduction = 200.0; // Hardcoded TN/KA style slab for example
        }

        double incomeTaxDeduction = calculateMonthlyTDS(annualCtc);

        double totalDeductions = lopDeduction + pfDeduction + ptDeduction + incomeTaxDeduction;
        double netSalary = grossSalary - totalDeductions;
        if (netSalary < 0) netSalary = 0; // Prevent negative payouts

        // 5. Construct Payslip Record
        Payslip payslip = new Payslip();
        payslip.setUser(employee);
        payslip.setMonth(month);
        payslip.setYear(year);
        payslip.setGrossSalary(grossSalary);
        payslip.setTotalDeductions(totalDeductions);
        payslip.setNetSalary(netSalary);
        payslip.setPfDeduction(pfDeduction);
        payslip.setIncomeTax(incomeTaxDeduction);
        payslip.setProfessionalTax(ptDeduction);
        payslip.setLopDeduction(lopDeduction);
        payslip.setWorkingDays(totalWorkingDays);
        payslip.setPresentDays(attendance.getPresentDays());
        payslip.setAbsentDays(absentDays);
        payslip.setGeneratedDate(LocalDate.now());

        // 6. Add Components
        payslip.addComponent(new PayslipComponent("Basic Salary", monthlyBasic, PayslipComponent.ComponentType.EARNING));
        payslip.addComponent(new PayslipComponent("House Rent Allowance (HRA)", monthlyHra, PayslipComponent.ComponentType.EARNING));
        payslip.addComponent(new PayslipComponent("Special Allowance", monthlySpecial, PayslipComponent.ComponentType.EARNING));

        if (lopDeduction > 0) {
            payslip.addComponent(new PayslipComponent("Loss of Pay (LOP)", lopDeduction, PayslipComponent.ComponentType.DEDUCTION));
        }
        if (pfDeduction > 0) {
            payslip.addComponent(new PayslipComponent("Provident Fund (PF)", pfDeduction, PayslipComponent.ComponentType.DEDUCTION));
        }
        if (ptDeduction > 0) {
            payslip.addComponent(new PayslipComponent("Professional Tax", ptDeduction, PayslipComponent.ComponentType.DEDUCTION));
        }
        if (incomeTaxDeduction > 0) {
            payslip.addComponent(new PayslipComponent("Income Tax (TDS)", incomeTaxDeduction, PayslipComponent.ComponentType.DEDUCTION));
        }

        payslipRepository.save(payslip);
        return true;
    }

    /**
     * Rough calculation of Indian New Tax Regime TDS for demonstration.
     */
    private double calculateMonthlyTDS(double annualIncome) {
        double tax = 0.0;
        // Standard deduction simplification
        double taxableIncome = annualIncome - 50000;
        if (taxableIncome <= 300000) return 0.0;

        if (taxableIncome > 300000 && taxableIncome <= 600000) {
            tax += (taxableIncome - 300000) * 0.05;
        } else if (taxableIncome > 600000 && taxableIncome <= 900000) {
            tax += 15000 + (taxableIncome - 600000) * 0.10;
        } else if (taxableIncome > 900000 && taxableIncome <= 1200000) {
            tax += 45000 + (taxableIncome - 900000) * 0.15;
        } else if (taxableIncome > 1200000 && taxableIncome <= 1500000) {
            tax += 90000 + (taxableIncome - 1200000) * 0.20;
        } else if (taxableIncome > 1500000) {
            tax += 150000 + (taxableIncome - 1500000) * 0.30;
        }
        
        // 87A rebate equivalent (simplified: no tax under 7L)
        if (annualIncome <= 700000) {
            return 0.0;
        }

        return tax / 12.0;
    }
    
    public List<Payslip> getPayslipsForUser(User user) {
        return payslipRepository.findByUserOrderByYearDescMonthDesc(user);
    }
}

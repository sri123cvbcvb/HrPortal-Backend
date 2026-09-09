package com.hrms.backend.controller;

import com.hrms.backend.dto.AttendanceSummaryDto;
import com.hrms.backend.dto.MessageResponse;
import com.hrms.backend.dto.SignupRequest;
import com.hrms.backend.dto.UpdateEmployeeRequest;
import com.hrms.backend.model.Role;
import com.hrms.backend.model.RoleName;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.RoleRepository;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final AttendanceService attendanceService;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder encoder, AttendanceService attendanceService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
        this.attendanceService = attendanceService;
    }

    @GetMapping("/employees")
    public ResponseEntity<?> getAllEmployees() {
        // Find users with ROLE_EMPLOYEE
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ROLE_EMPLOYEE))
                .toList();

        // Convert User entity to full DTO for response
        List<UserDto> response = employees.stream()
                .map(UserDto::fromUser)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employees/{id}")
    public ResponseEntity<?> getEmployeeById(@PathVariable Long id) {
        User employee = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Employee not found."));
        return ResponseEntity.ok(UserDto.fromUser(employee));
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id,
                                            @Valid @RequestBody UpdateEmployeeRequest request) {
        User employee = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Employee not found."));

        // Basic info
        if (StringUtils.hasText(request.getFirstName())) employee.setFirstName(request.getFirstName());
        if (StringUtils.hasText(request.getLastName()))  employee.setLastName(request.getLastName());
        if (StringUtils.hasText(request.getEmail()))     employee.setEmail(request.getEmail());

        // Optional password change
        if (StringUtils.hasText(request.getPassword())) {
            employee.setPassword(encoder.encode(request.getPassword()));
        }

        // Statutory
        if (request.getAadhaarNumber() != null) employee.setAadhaarNumber(request.getAadhaarNumber());
        if (request.getPanNumber()     != null) employee.setPanNumber(request.getPanNumber());

        // PF
        if (request.getPfApplicable()  != null) employee.setPfApplicable(request.getPfApplicable());
        if (request.getPfUan()         != null) employee.setPfUan(request.getPfUan());
        if (request.getPfAccount()     != null) employee.setPfAccount(request.getPfAccount());

        // Bank
        if (request.getBankName()          != null) employee.setBankName(request.getBankName());
        if (request.getBankAccountNumber() != null) employee.setBankAccountNumber(request.getBankAccountNumber());
        if (request.getIfscCode()          != null) employee.setIfscCode(request.getIfscCode());
        if (request.getAccountHolderName() != null) employee.setAccountHolderName(request.getAccountHolderName());

        // Salary
        if (request.getAnnualCtc()                   != null) employee.setAnnualCtc(request.getAnnualCtc());
        if (request.getBasicPercentage()             != null) employee.setBasicPercentage(request.getBasicPercentage());
        if (request.getHraPercentage()               != null) employee.setHraPercentage(request.getHraPercentage());
        if (request.getSpecialAllowancePercentage()  != null) employee.setSpecialAllowancePercentage(request.getSpecialAllowancePercentage());

        userRepository.save(employee);
        return ResponseEntity.ok(new MessageResponse("Employee updated successfully!"));
    }

    @PostMapping("/employees")
    public ResponseEntity<?> createEmployee(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = new User(signUpRequest.getFirstName(), signUpRequest.getLastName(),
                signUpRequest.getUsername(), signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Employee registered successfully!"));
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Employee not found."));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("Employee deleted successfully."));
    }

    @GetMapping("/employees/{id}/attendance/summary")
    public ResponseEntity<?> getEmployeeAttendanceSummary(
            @PathVariable Long id,
            @RequestParam int year,
            @RequestParam int month) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        AttendanceSummaryDto summary = attendanceService.getMonthlySummary(user, year, month);
        return ResponseEntity.ok(summary);
    }

    // DTO class — full employee details
    static class UserDto {
        private Long id;
        private String firstName;
        private String lastName;
        private String username;
        private String email;
        // Statutory
        private String aadhaarNumber;
        private String panNumber;
        // PF
        private Boolean pfApplicable;
        private String pfUan;
        private String pfAccount;
        // Bank
        private String bankName;
        private String bankAccountNumber;
        private String ifscCode;
        private String accountHolderName;
        // Salary
        private Double annualCtc;
        private Double basicPercentage;
        private Double hraPercentage;
        private Double specialAllowancePercentage;

        public static UserDto fromUser(User u) {
            UserDto dto = new UserDto();
            dto.id = u.getId();
            dto.firstName = u.getFirstName();
            dto.lastName = u.getLastName();
            dto.username = u.getUsername();
            dto.email = u.getEmail();
            dto.aadhaarNumber = u.getAadhaarNumber();
            dto.panNumber = u.getPanNumber();
            dto.pfApplicable = u.getPfApplicable();
            dto.pfUan = u.getPfUan();
            dto.pfAccount = u.getPfAccount();
            dto.bankName = u.getBankName();
            dto.bankAccountNumber = u.getBankAccountNumber();
            dto.ifscCode = u.getIfscCode();
            dto.accountHolderName = u.getAccountHolderName();
            dto.annualCtc = u.getAnnualCtc();
            dto.basicPercentage = u.getBasicPercentage();
            dto.hraPercentage = u.getHraPercentage();
            dto.specialAllowancePercentage = u.getSpecialAllowancePercentage();
            return dto;
        }

        private UserDto() {}

        public Long getId() { return id; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getAadhaarNumber() { return aadhaarNumber; }
        public String getPanNumber() { return panNumber; }
        public Boolean getPfApplicable() { return pfApplicable; }
        public String getPfUan() { return pfUan; }
        public String getPfAccount() { return pfAccount; }
        public String getBankName() { return bankName; }
        public String getBankAccountNumber() { return bankAccountNumber; }
        public String getIfscCode() { return ifscCode; }
        public String getAccountHolderName() { return accountHolderName; }
        public Double getAnnualCtc() { return annualCtc; }
        public Double getBasicPercentage() { return basicPercentage; }
        public Double getHraPercentage() { return hraPercentage; }
        public Double getSpecialAllowancePercentage() { return specialAllowancePercentage; }
    }
}

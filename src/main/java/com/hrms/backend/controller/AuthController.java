package com.hrms.backend.controller;

import com.hrms.backend.dto.JwtResponse;
import com.hrms.backend.dto.LoginRequest;
import com.hrms.backend.dto.MessageResponse;
import com.hrms.backend.dto.SignupRequest;
import com.hrms.backend.model.Role;
import com.hrms.backend.model.RoleName;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.RoleRepository;
import com.hrms.backend.repository.UserRepository;
import com.hrms.backend.security.JwtUtils;
import com.hrms.backend.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
            RoleRepository roleRepository, PasswordEncoder encoder, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login request received for user: {}", loginRequest.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            logger.info("Login successful for user: {}", loginRequest.getUsername());
            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    roles));
        } catch (BadCredentialsException e) {
            logger.error("Authentication failed for user {}: Invalid credentials", loginRequest.getUsername());
            throw e;
        } catch (Exception e) {
            logger.error("Authentication error for user {}: {}", loginRequest.getUsername(), e.getMessage());
            throw e;
        }
    }

    // Default admin registration should ideally be done differently or protected,
    // but keeping it open for initial setup
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getFirstName(), signUpRequest.getLastName(),
                signUpRequest.getUsername(), signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        // Map Statutory & Payroll Fields
        user.setAadhaarNumber(signUpRequest.getAadhaarNumber());
        user.setPanNumber(signUpRequest.getPanNumber());
        user.setPfApplicable(signUpRequest.getPfApplicable());
        user.setPfUan(signUpRequest.getPfUan());
        user.setPfAccount(signUpRequest.getPfAccount());
        user.setBankName(signUpRequest.getBankName());
        user.setBankAccountNumber(signUpRequest.getBankAccountNumber());
        user.setIfscCode(signUpRequest.getIfscCode());
        user.setAccountHolderName(signUpRequest.getAccountHolderName());
        user.setAnnualCtc(signUpRequest.getAnnualCtc());
        user.setBasicPercentage(signUpRequest.getBasicPercentage());
        user.setHraPercentage(signUpRequest.getHraPercentage());
        user.setSpecialAllowancePercentage(signUpRequest.getSpecialAllowancePercentage());

        Set<String> strRoles = signUpRequest.getRoles();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                if ("admin".equals(role)) {
                    Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    roles.add(adminRole);
                } else {
                    Role userRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    roles.add(userRole);
                }
            });
        }

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}

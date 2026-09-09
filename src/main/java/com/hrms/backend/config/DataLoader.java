package com.hrms.backend.config;

import com.hrms.backend.model.Role;
import com.hrms.backend.model.RoleName;
import com.hrms.backend.model.User;
import com.hrms.backend.repository.RoleRepository;
import com.hrms.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class DataLoader {

    @Value("${app.admin.default-password}")
    private String adminDefaultPassword;

    @Bean
    CommandLineRunner initDatabase(RoleRepository roleRepository, UserRepository userRepository,
            PasswordEncoder encoder) {
        return args -> {
            // Seed roles if not present
            if (roleRepository.count() == 0) {
                roleRepository.save(new Role(null, RoleName.ROLE_EMPLOYEE));
                roleRepository.save(new Role(null, RoleName.ROLE_ADMIN));
            }

            // Seed default admin if not present
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User("Admin", "User", "admin", "admin@hrms.com", encoder.encode(adminDefaultPassword));
                Set<Role> roles = new HashSet<>();
                Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                        .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                roles.add(adminRole);
                admin.setRoles(roles);
                userRepository.save(admin);
            }
        };
    }
}

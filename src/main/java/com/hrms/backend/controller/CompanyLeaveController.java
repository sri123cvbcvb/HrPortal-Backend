package com.hrms.backend.controller;

import com.hrms.backend.dto.MessageResponse;
import com.hrms.backend.model.CompanyLeave;
import com.hrms.backend.repository.CompanyLeaveRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/leaves")
public class CompanyLeaveController {

    private final CompanyLeaveRepository companyLeaveRepository;

    public CompanyLeaveController(CompanyLeaveRepository companyLeaveRepository) {
        this.companyLeaveRepository = companyLeaveRepository;
    }

    @PostMapping
    public ResponseEntity<?> addLeave(@Valid @RequestBody CompanyLeave leave) {
        companyLeaveRepository.save(leave);
        return ResponseEntity.ok(new MessageResponse("Company leave added successfully."));
    }

    @GetMapping
    public ResponseEntity<List<CompanyLeave>> getAllLeaves() {
        return ResponseEntity.ok(companyLeaveRepository.findAll());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<CompanyLeave>> getUpcomingLeaves() {
        LocalDate today = LocalDate.now();
        List<CompanyLeave> upcoming = companyLeaveRepository.findAll().stream()
                .filter(leave -> !leave.getDate().isBefore(today))
                .collect(Collectors.toList());
        return ResponseEntity.ok(upcoming);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLeave(@PathVariable Long id) {
        companyLeaveRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("Company leave deleted successfully."));
    }
}

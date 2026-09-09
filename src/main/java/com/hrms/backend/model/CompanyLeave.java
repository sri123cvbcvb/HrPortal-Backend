package com.hrms.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "company_leaves")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyLeave {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    @NotBlank(message = "Reason is required")
    private String description;

    public CompanyLeave(LocalDate date, String description) {
        this.date = date;
        this.description = description;
    }
}

package com.hrms.backend.dto;

import com.hrms.backend.model.LeaveRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveRequestDto {

    @NotNull(message = "Leave Type is required")
    private Long leaveTypeId;

    @NotNull(message = "From Date is required")
    private LocalDate fromDate;

    @NotNull(message = "To Date is required")
    private LocalDate toDate;

    @NotNull(message = "Session From is required")
    private LeaveRequest.LeaveSession sessionFrom;

    @NotNull(message = "Session To is required")
    private LeaveRequest.LeaveSession sessionTo;

    @NotBlank(message = "Reason is required")
    private String reason;
}

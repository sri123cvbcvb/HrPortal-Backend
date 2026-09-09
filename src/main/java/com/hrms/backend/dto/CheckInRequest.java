package com.hrms.backend.dto;

import lombok.Data;

@Data
public class CheckInRequest {
    private Double latitude;
    private Double longitude;
    private String deviceName; // e.g., "Chrome on Windows", "Mobile Safari on iPhone"
}

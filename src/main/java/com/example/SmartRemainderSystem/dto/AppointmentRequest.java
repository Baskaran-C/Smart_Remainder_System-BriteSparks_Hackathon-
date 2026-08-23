package com.example.SmartRemainderSystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String email;

    private String phone;

    @NotBlank(message = "Office name is required")
    private String officeName;

    @NotBlank(message = "Service is required")
    private String service;

    @NotNull(message = "Appointment date and time is required")
    private LocalDateTime appointmentTime;

    // Office GPS coordinates (pre-configured per office)
    private Double officeLatitude;
    private Double officeLongitude;
    private String officeAddress;
}

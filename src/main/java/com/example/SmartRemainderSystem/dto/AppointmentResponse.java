package com.example.SmartRemainderSystem.dto;

import com.example.SmartRemainderSystem.entity.enums.AppointmentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String officeName;
    private String service;
    private LocalDateTime appointmentTime;
    private String officeAddress;
    private AppointmentStatus status;
    private LocalDateTime createdAt;
    private String reminderStatus; // Summary of current reminder state
    private String lastDecision;   // Most recent decision engine outcome
}

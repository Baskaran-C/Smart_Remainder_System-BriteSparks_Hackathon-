package com.example.SmartRemainderSystem.entity.enums;

public enum EventType {
    SCHEDULED,   // Reminder entered the system
    SENT,        // Email sent to SMTP
    DELIVERED,   // Delivery confirmed
    SEEN,        // User opened or clicked the reminder
    CONFIRMED,   // User confirmed the appointment
    CANCELLED,   // Appointment/reminder cancelled
    FAILED,      // Delivery failed
    RETRY        // Recovery retry scheduled
}

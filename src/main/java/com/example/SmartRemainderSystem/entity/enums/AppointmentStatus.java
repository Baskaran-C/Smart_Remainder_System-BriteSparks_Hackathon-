package com.example.SmartRemainderSystem.entity.enums;

public enum AppointmentStatus {
    PENDING,      // Booked, awaiting confirmation
    CONFIRMED,    // User confirmed
    CANCELLED,    // User cancelled
    COMPLETED,    // Appointment attended
    NO_SHOW       // User did not attend
}

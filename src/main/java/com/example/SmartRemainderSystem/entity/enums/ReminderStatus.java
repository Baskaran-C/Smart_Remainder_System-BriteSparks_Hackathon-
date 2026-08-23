package com.example.SmartRemainderSystem.entity.enums;

public enum ReminderStatus {
    SCHEDULED,   // Created, awaiting evaluation
    WAITING,     // Decision engine said WAIT — scheduled for re-evaluation
    SENT,        // Email dispatched to SMTP
    DELIVERED,   // Confirmed delivered (best-effort tracking)
    SEEN,        // User opened / acknowledged (tracked via read-receipt or link click)
    CONFIRMED,   // User actively confirmed the appointment via reminder link
    CANCELLED,   // Appointment was cancelled; reminder is no longer relevant
    FAILED,      // SMTP or delivery failure
    STOPPED      // Decision engine terminated reminder chain (harassment firewall / max retries)
}

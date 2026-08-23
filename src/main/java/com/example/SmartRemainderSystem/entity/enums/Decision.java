package com.example.SmartRemainderSystem.entity.enums;

public enum Decision {
    SEND,      // Send the reminder now
    WAIT,      // Defer — re-evaluate after cooldown
    MOVE,      // Shift planned_time to a better window
    RECOVER,   // Previous send failed — schedule a retry
    STOP,      // Terminate the reminder chain for this appointment
    WITHHELD   // CR-2026/11: Contact blocked by rolling 7-day limit firewall
}

package com.example.SmartRemainderSystem.dto;

import lombok.Data;

@Data
public class AdminDashboardResponse {

    // Appointment stats
    private long totalAppointments;
    private long pendingAppointments;
    private long confirmedAppointments;
    private long cancelledAppointments;
    private long completedAppointments;
    private long noShowAppointments;

    // Reminder stats
    private long totalReminders;
    private long scheduledReminders;
    private long sentReminders;
    private long deliveredReminders;
    private long seenReminders;
    private long confirmedReminders;
    private long failedReminders;
    private long stoppedReminders;

    // Decision engine stats
    private long totalDecisions;
    private long sendDecisions;
    private long waitDecisions;
    private long moveDecisions;
    private long recoverDecisions;
    private long stopDecisions;

    // Effectiveness metrics (as percentages)
    private double deliveryRate;    // sent → delivered %
    private double seenRate;        // delivered → seen %
    private double confirmRate;     // seen → confirmed %
    private double noShowRate;      // appointments → no-show %
}

package com.example.SmartRemainderSystem.controller;

import com.example.SmartRemainderSystem.dto.AdminDashboardResponse;
import com.example.SmartRemainderSystem.dto.AppointmentResponse;
import com.example.SmartRemainderSystem.dto.ContactLimitResult;
import com.example.SmartRemainderSystem.dto.DecisionResponse;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.repository.UserRepository;
import com.example.SmartRemainderSystem.service.AnalyticsService;
import com.example.SmartRemainderSystem.service.AppointmentService;
import com.example.SmartRemainderSystem.service.ContactLimitService;
import com.example.SmartRemainderSystem.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AnalyticsService analyticsService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final ContactLimitService contactLimitService;
    private final UserRepository userRepository;

    /**
     * GET /api/admin/dashboard
     * Returns all analytics metrics for the admin dashboard.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> dashboard() {
        return ResponseEntity.ok(analyticsService.getDashboard());
    }

    /**
     * GET /api/admin/appointments
     * Returns all appointments for the admin table.
     */
    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentResponse>> appointments() {
        return ResponseEntity.ok(appointmentService.getAll());
    }

    /**
     * GET /api/admin/appointments/{id}/decisions
     * Returns the full decision timeline for one appointment.
     */
    @GetMapping("/appointments/{id}/decisions")
    public ResponseEntity<List<DecisionResponse>> decisionTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getDecisionTimeline(id));
    }

    /**
     * GET /api/admin/appointments/{id}/reminders
     * Returns full reminder details for one appointment.
     */
    @GetMapping("/appointments/{id}/reminders")
    public ResponseEntity<?> reminderDetails(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getRemindersForAppointment(id));
    }

    /**
     * GET /api/admin/residents/{userId}/contact-limit
     *
     * CR-2026/11: Returns the rolling 7-day contact limit status for a resident.
     *
     * Response includes:
     *  - currentCount    : contacts made in the rolling window
     *  - maximumCount    : regulatory limit (2)
     *  - remainingContacts: how many more contacts are allowed
     *  - windowStart/End : rolling window boundaries
     *  - nextPermittedTime: when the next contact will be allowed (if at limit)
     *  - recentContacts  : list of attempts in the window (date, channel, outcome)
     */
    @GetMapping("/residents/{userId}/contact-limit")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> residentContactLimit(@PathVariable Long userId) {
        User resident = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Resident not found: " + userId));

        ContactLimitResult result = contactLimitService.getResidentContactSummary(resident);

        // Build a clean JSON-friendly response map safely
        var contacts = result.getRecentContacts().stream().map(ca -> {
            java.util.Map<String, Object> c = new java.util.HashMap<>();
            c.put("id", ca.getId());
            c.put("appointmentId", ca.getAppointment() != null ? ca.getAppointment().getId() : null);
            c.put("channel", ca.getChannel());
            c.put("attemptedAt", ca.getAttemptedAt() != null ? ca.getAttemptedAt().toString() : null);
            c.put("outcome", ca.getOutcome());
            return c;
        }).toList();

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("residentId", result.getResidentId());
        response.put("allowed", result.isAllowed());
        response.put("currentCount", result.getCurrentCount());
        response.put("maximumCount", result.getMaximumCount());
        response.put("remainingContacts", result.getRemainingContacts());
        response.put("windowStart", result.getWindowStart().toString());
        response.put("windowEnd", result.getWindowEnd().toString());
        response.put("nextPermittedTime", result.getNextPermittedTime() != null
                ? result.getNextPermittedTime().toString() : null);
        response.put("recentContacts", contacts);

        return ResponseEntity.ok(response);
    }
}

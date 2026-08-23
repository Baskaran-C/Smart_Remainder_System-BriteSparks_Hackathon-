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
    public ResponseEntity<?> residentContactLimit(@PathVariable Long userId) {
        User resident = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Resident not found: " + userId));

        ContactLimitResult result = contactLimitService.getResidentContactSummary(resident);

        // Build a clean JSON-friendly response map
        var contacts = result.getRecentContacts().stream().map(ca -> Map.of(
                "id", ca.getId(),
                "appointmentId", ca.getAppointment().getId(),
                "channel", ca.getChannel(),
                "attemptedAt", ca.getAttemptedAt().toString(),
                "outcome", ca.getOutcome()
        )).toList();

        Map<String, Object> response = Map.of(
                "residentId", result.getResidentId(),
                "allowed", result.isAllowed(),
                "currentCount", result.getCurrentCount(),
                "maximumCount", result.getMaximumCount(),
                "remainingContacts", result.getRemainingContacts(),
                "windowStart", result.getWindowStart().toString(),
                "windowEnd", result.getWindowEnd().toString(),
                "nextPermittedTime", result.getNextPermittedTime() != null
                        ? result.getNextPermittedTime().toString() : null,
                "recentContacts", contacts
        );

        return ResponseEntity.ok(response);
    }
}

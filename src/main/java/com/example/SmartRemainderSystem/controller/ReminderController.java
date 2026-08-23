package com.example.SmartRemainderSystem.controller;

import com.example.SmartRemainderSystem.dto.DecisionResponse;
import com.example.SmartRemainderSystem.dto.ReminderResponse;
import com.example.SmartRemainderSystem.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * GET /api/appointments/{id}/reminders
     * Get all reminders (with events and decisions) for an appointment.
     */
    @GetMapping("/appointments/{id}/reminders")
    public ResponseEntity<List<ReminderResponse>> getReminders(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getRemindersForAppointment(id));
    }

    /**
     * GET /api/appointments/{id}/decisions
     * Get the complete decision timeline for an appointment.
     */
    @GetMapping("/appointments/{id}/decisions")
    public ResponseEntity<List<DecisionResponse>> getDecisions(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getDecisionTimeline(id));
    }

    /**
     * POST /api/reminders/{id}/events?type=SEEN
     * Record a reminder event. Called when user clicks a tracking link in the email.
     * This is the mechanism by which SEEN and CONFIRMED states are tracked.
     */
    @PostMapping("/reminders/{id}/events")
    public ResponseEntity<String> recordEvent(
            @PathVariable Long id,
            @RequestParam String type) {
        reminderService.recordEvent(id, type);
        return ResponseEntity.ok("Event recorded: " + type);
    }

    /**
     * GET /api/reminders/{id}/events?type=SEEN  (tracking link — redirects)
     * When user clicks the email link, record SEEN and redirect to appointment page.
     */
    @GetMapping("/reminders/{id}/events")
    public ResponseEntity<Void> trackEvent(
            @PathVariable Long id,
            @RequestParam String type) {
        reminderService.recordEvent(id, type);
        // Redirect to the appointment page
        return ResponseEntity.status(302)
                .header("Location", "/my-appointment.html?tracked=true&event=" + type)
                .build();
    }
}

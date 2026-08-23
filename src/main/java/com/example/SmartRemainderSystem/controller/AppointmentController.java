package com.example.SmartRemainderSystem.controller;

import com.example.SmartRemainderSystem.dto.AppointmentRequest;
import com.example.SmartRemainderSystem.dto.AppointmentResponse;
import com.example.SmartRemainderSystem.service.AppointmentService;
import com.example.SmartRemainderSystem.service.ActivityService;
import com.example.SmartRemainderSystem.entity.enums.ActivityType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ActivityService activityService;

    /**
     * POST /api/appointments
     * Book an appointment. Reminders are auto-scheduled by the backend.
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse response = appointmentService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/appointments/{id}
     * Get appointment details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> get(@PathVariable Long id) {
        AppointmentResponse response = appointmentService.getById(id);
        // Record that user viewed the appointment (for active-window detection)
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/appointments/{id}/reschedule
     * Reschedule an appointment — invalidates old reminders, creates new plan.
     */
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        LocalDateTime newTime = LocalDateTime.parse(body.get("appointmentTime"));
        return ResponseEntity.ok(appointmentService.reschedule(id, newTime));
    }

    /**
     * PATCH /api/appointments/{id}/cancel
     * Cancel an appointment.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.cancel(id));
    }
}

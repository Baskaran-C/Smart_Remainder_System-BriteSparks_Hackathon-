package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.dto.AppointmentRequest;
import com.example.SmartRemainderSystem.dto.AppointmentResponse;
import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.DecisionLog;
import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.ReminderEvent;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.entity.enums.AppointmentStatus;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import com.example.SmartRemainderSystem.repository.AppointmentRepository;
import com.example.SmartRemainderSystem.repository.DecisionLogRepository;
import com.example.SmartRemainderSystem.repository.ReminderEventRepository;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import com.example.SmartRemainderSystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;
    private final DecisionLogRepository decisionLogRepository;

    @Value("${app.reminder.first-reminder-hours-before:24}")
    private int firstReminderHoursBefore;

    @Value("${app.reminder.evaluation-interval-minutes:2}")
    private int evaluationIntervalMinutes;

    /**
     * Book an appointment and automatically create the initial reminder plan.
     * The user should NEVER manually create reminders.
     */
    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request) {
        User user = resolveUser(request);

        Appointment appointment = new Appointment();
        appointment.setUser(user);
        appointment.setOfficeName(request.getOfficeName());
        appointment.setService(request.getService());
        appointment.setAppointmentTime(request.getAppointmentTime());
        appointment.setOfficeLatitude(request.getOfficeLatitude());
        appointment.setOfficeLongitude(request.getOfficeLongitude());
        appointment.setOfficeAddress(request.getOfficeAddress());
        appointment.setStatus(AppointmentStatus.PENDING);

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment {} created for user {} at {}", appointment.getId(),
                user.getId(), appointment.getAppointmentTime());

        scheduleInitialReminder(appointment);

        return toResponse(appointment);
    }

    /**
     * Reschedule: invalidate old reminders, create a new reminder plan.
     */
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, LocalDateTime newTime) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + appointmentId));

        List<Reminder> activeReminders = reminderRepository.findByAppointment(appointment).stream()
                .filter(r -> r.getStatus() == ReminderStatus.SCHEDULED ||
                             r.getStatus() == ReminderStatus.WAITING)
                .collect(Collectors.toList());

        for (Reminder r : activeReminders) {
            r.setStatus(ReminderStatus.STOPPED);
            r.setFailureReason("Appointment rescheduled — reminder invalidated.");
            reminderRepository.save(r);
            reminderEventRepository.save(new ReminderEvent(r, EventType.CANCELLED,
                    "Invalidated: appointment rescheduled from " +
                    appointment.getAppointmentTime() + " to " + newTime));
        }

        appointment.setAppointmentTime(newTime);
        appointment = appointmentRepository.save(appointment);
        log.info("Appointment {} rescheduled to {}. {} old reminders invalidated.",
                appointmentId, newTime, activeReminders.size());

        scheduleInitialReminder(appointment);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + appointmentId));
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment = appointmentRepository.save(appointment);
        log.info("Appointment {} cancelled.", appointmentId);
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + appointmentId));
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAll() {
        return appointmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────

    private void scheduleInitialReminder(Appointment appointment) {
        Reminder reminder = new Reminder();
        reminder.setAppointment(appointment);
        reminder.setStatus(ReminderStatus.SCHEDULED);

        LocalDateTime planned = appointment.getAppointmentTime()
                .minusHours(firstReminderHoursBefore);
        if (planned.isBefore(LocalDateTime.now())) {
            planned = LocalDateTime.now().plusMinutes(evaluationIntervalMinutes);
        }

        reminder.setPlannedTime(planned);
        reminder.setNextEvaluationTime(planned);
        reminder = reminderRepository.save(reminder);

        reminderEventRepository.save(new ReminderEvent(reminder, EventType.SCHEDULED,
                "Auto-scheduled after appointment booking. Planned: " + planned));

        log.info("Initial reminder {} scheduled for appointment {} at {}",
                reminder.getId(), appointment.getId(), planned);
    }

    private User resolveUser(AppointmentRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Optional<User> existing = userRepository.findByEmail(request.getEmail());
            if (existing.isPresent()) {
                User u = existing.get();
                u.setName(request.getName());
                return userRepository.save(u);
            }
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            Optional<User> existing = userRepository.findByPhone(request.getPhone());
            if (existing.isPresent()) return existing.get();
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        return userRepository.save(user);
    }

    private AppointmentResponse toResponse(Appointment a) {
        AppointmentResponse r = new AppointmentResponse();
        r.setId(a.getId());
        r.setUserId(a.getUser().getId());
        r.setUserName(a.getUser().getName());
        r.setUserEmail(a.getUser().getEmail());
        r.setUserPhone(a.getUser().getPhone());
        r.setOfficeName(a.getOfficeName());
        r.setService(a.getService());
        r.setAppointmentTime(a.getAppointmentTime());
        r.setOfficeAddress(a.getOfficeAddress());
        r.setStatus(a.getStatus());
        r.setCreatedAt(a.getCreatedAt());

        reminderRepository.findTopByAppointmentOrderByCreatedAtDesc(a).ifPresent(rem -> {
            r.setReminderStatus(rem.getStatus().name());
        });

        List<DecisionLog> decisions = decisionLogRepository.findByAppointmentId(a.getId());
        if (!decisions.isEmpty()) {
            DecisionLog last = decisions.get(decisions.size() - 1);
            r.setLastDecision(last.getDecision() + ": " + last.getReason());
        }

        return r;
    }
}

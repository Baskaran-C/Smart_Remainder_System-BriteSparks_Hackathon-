package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.dto.DecisionResponse;
import com.example.SmartRemainderSystem.dto.ReminderResponse;
import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.DecisionLog;
import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.ReminderEvent;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import com.example.SmartRemainderSystem.repository.AppointmentRepository;
import com.example.SmartRemainderSystem.repository.DecisionLogRepository;
import com.example.SmartRemainderSystem.repository.ReminderEventRepository;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import com.example.SmartRemainderSystem.entity.enums.ActivityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;
    private final DecisionLogRepository decisionLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final ActivityService activityService;

    /**
     * Get all reminders with their events and decisions for an appointment.
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> getRemindersForAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + appointmentId));

        return reminderRepository.findByAppointmentOrderByCreatedAtAsc(appointment).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Record a user interaction event for a reminder (SEEN, CONFIRMED, etc.).
     * Called when user clicks a tracking link in the email.
     */
    @Transactional
    public void recordEvent(Long reminderId, String eventTypeStr) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("Reminder not found: " + reminderId));

        EventType eventType;
        try {
            eventType = EventType.valueOf(eventTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown event type: " + eventTypeStr);
        }

        // Update reminder status based on event
        switch (eventType) {
            case SEEN -> {
                if (reminder.getStatus() == ReminderStatus.SENT ||
                    reminder.getStatus() == ReminderStatus.DELIVERED) {
                    reminder.setStatus(ReminderStatus.SEEN);
                    reminderRepository.save(reminder);
                }
                // Record user activity for active-window analysis
                activityService.record(reminder.getAppointment().getUser(),
                        ActivityType.REMINDER_SEEN, reminderId);
            }
            case CONFIRMED -> {
                reminder.setStatus(ReminderStatus.CONFIRMED);
                reminderRepository.save(reminder);
                activityService.record(reminder.getAppointment().getUser(),
                        ActivityType.REMINDER_CONFIRMED, reminderId);
                log.info("Appointment {} confirmed by user via reminder link.",
                        reminder.getAppointment().getId());
            }
            case CANCELLED -> {
                reminder.setStatus(ReminderStatus.CANCELLED);
                reminderRepository.save(reminder);
                // Also cancel the appointment
                Appointment appt = reminder.getAppointment();
                appt.setStatus(com.example.SmartRemainderSystem.entity.enums.AppointmentStatus.CANCELLED);
                appointmentRepository.save(appt);
                log.info("Appointment {} cancelled by user via reminder link.", appt.getId());
            }
            case DELIVERED -> {
                if (reminder.getStatus() == ReminderStatus.SENT) {
                    reminder.setStatus(ReminderStatus.DELIVERED);
                    reminderRepository.save(reminder);
                }
            }
            default -> log.warn("Unhandled event type: {}", eventType);
        }

        reminderEventRepository.save(new ReminderEvent(reminder, eventType,
                "Recorded at " + LocalDateTime.now()));
    }

    /**
     * Get the full decision timeline for an appointment.
     */
    @Transactional(readOnly = true)
    public List<DecisionResponse> getDecisionTimeline(Long appointmentId) {
        return decisionLogRepository.findByAppointmentId(appointmentId).stream()
                .map(this::toDecisionResponse)
                .collect(Collectors.toList());
    }

    private ReminderResponse toResponse(Reminder r) {
        ReminderResponse resp = new ReminderResponse();
        resp.setId(r.getId());
        resp.setAppointmentId(r.getAppointment().getId());
        resp.setPlannedTime(r.getPlannedTime());
        resp.setActualSentTime(r.getActualSentTime());
        resp.setStatus(r.getStatus());
        resp.setAttemptNumber(r.getAttemptNumber());
        resp.setNextEvaluationTime(r.getNextEvaluationTime());
        resp.setFailureReason(r.getFailureReason());
        resp.setCreatedAt(r.getCreatedAt());

        List<ReminderResponse.ReminderEventResponse> events =
                reminderEventRepository.findByReminderOrderByEventTimeAsc(r).stream()
                        .map(e -> {
                            ReminderResponse.ReminderEventResponse er = new ReminderResponse.ReminderEventResponse();
                            er.setId(e.getId());
                            er.setEventType(e.getEventType().name());
                            er.setEventTime(e.getEventTime());
                            er.setDetail(e.getDetail());
                            return er;
                        }).collect(Collectors.toList());
        resp.setEvents(events);

        List<DecisionResponse> decisions =
                decisionLogRepository.findByReminderOrderByCreatedAtAsc(r).stream()
                        .map(this::toDecisionResponse)
                        .collect(Collectors.toList());
        resp.setDecisions(decisions);

        return resp;
    }

    private DecisionResponse toDecisionResponse(DecisionLog dl) {
        DecisionResponse dr = new DecisionResponse();
        dr.setId(dl.getId());
        dr.setReminderId(dl.getReminder().getId());
        dr.setDecision(dl.getDecision());
        dr.setReason(dl.getReason());
        dr.setFactorsChecked(dl.getFactorsChecked());
        dr.setCreatedAt(dl.getCreatedAt());
        return dr;
    }
}

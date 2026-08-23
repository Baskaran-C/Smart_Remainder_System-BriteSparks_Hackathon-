package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.dto.ContactLimitResult;
import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.DecisionLog;
import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.ReminderEvent;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.entity.enums.AppointmentStatus;
import com.example.SmartRemainderSystem.entity.enums.Decision;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import com.example.SmartRemainderSystem.repository.DecisionLogRepository;
import com.example.SmartRemainderSystem.repository.ReminderEventRepository;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * =====================================================================
 * REMINDER DECISION ENGINE
 * =====================================================================
 *
 * Multi-stage pipeline evaluated for every scheduled reminder:
 *
 *   Stage 1: APPOINTMENT_STATUS      — Cancelled / Completed / No-Show → STOP
 *   Stage 2: CONTACT_LIMIT_FIREWALL  — CR-2026/11: ≥2 contacts in 7 days → WITHHELD
 *   Stage 3: TIME_CHECK              — Appointment already passed → STOP
 *   Stage 4: HARASSMENT_FIREWALL     — Per-appointment max reminders → STOP
 *   Stage 5: PREVIOUS_RESPONSE       — User CONFIRMED / CANCELLED / cooldown
 *   Stage 6: ACTIVE_TIME_WINDOW      — User is reachable right now?
 *   Stage 7: LOCATION_CHECK          — Departure time approaching?
 *   Stage 8: CONFLICT_CHECK          — Conflicting appointments?
 *   Stage 9: FINAL_DECISION          — SEND / WAIT / MOVE
 *
 * Every decision is stored in decision_logs for full explainability.
 * =====================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderDecisionService {

    private final ReminderRepository reminderRepository;
    private final DecisionLogRepository decisionLogRepository;
    private final ReminderEventRepository reminderEventRepository;
    private final ActivityService activityService;
    private final LocationService locationService;
    private final ConflictService conflictService;
    private final NotificationService notificationService;
    private final RecoveryService recoveryService;
    private final ContactLimitService contactLimitService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.reminder.max-per-appointment:5}")
    private int maxRemindersPerAppointment;

    @Value("${app.reminder.cooldown-minutes:30}")
    private int cooldownMinutes;

    @Value("${app.reminder.evaluation-interval-minutes:2}")
    private int evaluationIntervalMinutes;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // =========================================================
    // MAIN ENTRY POINT — called by ReminderScheduler
    // =========================================================

    @Transactional
    public void evaluate(Long reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("Reminder not found: " + reminderId));
        Appointment appointment = reminder.getAppointment();
        User resident = appointment.getUser();
        List<String> factors = new ArrayList<>();

        log.info("Evaluating reminder {} for appointment {}", reminder.getId(), appointment.getId());

        // ── Stage 1: Appointment Status Check ───────────────
        factors.add("APPOINTMENT_STATUS");
        AppointmentStatus apptStatus = appointment.getStatus();
        if (apptStatus == AppointmentStatus.CANCELLED) {
            decide(reminder, Decision.STOP, "Appointment has been CANCELLED. No further reminders needed.", factors);
            return;
        }
        if (apptStatus == AppointmentStatus.COMPLETED) {
            decide(reminder, Decision.STOP, "Appointment is marked COMPLETED.", factors);
            return;
        }
        if (apptStatus == AppointmentStatus.NO_SHOW) {
            decide(reminder, Decision.STOP, "Appointment is marked NO_SHOW.", factors);
            return;
        }

        // ── Stage 2: Contact Limit Firewall (CR-2026/11) ────
        // Maximum 2 outbound contacts per resident in any rolling 7-day window.
        // Applies across all channels, all appointments, including failed attempts.
        // Historical contacts (before this feature) count if within the window.
        factors.add("CONTACT_LIMIT_FIREWALL");
        ContactLimitResult limitResult = contactLimitService.checkLimit(resident);

        if (!limitResult.isAllowed()) {
            // Build a structured, evidence-ready reason string
            StringBuilder withheldReason = new StringBuilder();
            withheldReason.append(String.format(
                    "REGULATORY BLOCK (CR-2026/11): Resident %d has reached the maximum of %d " +
                    "outbound contacts in the rolling 7-day window.\n",
                    resident.getId(), limitResult.getMaximumCount()));
            withheldReason.append(String.format(
                    "Contacts counted: %d / %d\n",
                    limitResult.getCurrentCount(), limitResult.getMaximumCount()));
            withheldReason.append(String.format(
                    "Window: %s to %s\n",
                    limitResult.getWindowStart().format(DT_FMT),
                    limitResult.getWindowEnd().format(DT_FMT)));
            if (!limitResult.getRecentContacts().isEmpty()) {
                withheldReason.append("Contact history in window:\n");
                limitResult.getRecentContacts().forEach(ca ->
                        withheldReason.append(String.format(
                                "  - %s via %s [%s]\n",
                                ca.getAttemptedAt().format(DT_FMT), ca.getChannel(), ca.getOutcome())));
            }
            if (limitResult.getNextPermittedTime() != null) {
                withheldReason.append(String.format(
                        "Next contact permitted: %s\n",
                        limitResult.getNextPermittedTime().format(DT_FMT)));
            }
            withheldReason.append(String.format(
                    "Appointment ID: %d | Resident ID: %d",
                    appointment.getId(), resident.getId()));

            // Record WITHHELD decision — does NOT create a ContactAttempt
            decideWithheld(reminder, withheldReason.toString(), factors);
            broadcastDecision(reminder, Decision.WITHHELD, appointment);
            return;
        }

        // ── Stage 3: Time Check ─────────────────────────────
        factors.add("TIME_CHECK");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime apptTime = appointment.getAppointmentTime();
        long minutesToAppointment = ChronoUnit.MINUTES.between(now, apptTime);

        if (minutesToAppointment <= 0) {
            decide(reminder, Decision.STOP, "Appointment time has already passed.", factors);
            return;
        }

        // ── Stage 4: Harassment Firewall ────────────────────
        factors.add("HARASSMENT_FIREWALL");
        long sentCount = reminderRepository.countSentReminders(appointment);
        if (sentCount >= maxRemindersPerAppointment) {
            decide(reminder, Decision.STOP,
                    String.format("Harassment firewall triggered: %d reminders already sent (max: %d). Stopping.",
                            sentCount, maxRemindersPerAppointment), factors);
            return;
        }

        // ── Stage 5: Previous Response Check ────────────────
        factors.add("PREVIOUS_RESPONSE");
        Optional<Reminder> lastReminderOpt = reminderRepository.findTopByAppointmentOrderByCreatedAtDesc(appointment);

        if (lastReminderOpt.isPresent()) {
            Reminder last = lastReminderOpt.get();

            // User confirmed → no more reminders needed
            if (last.getStatus() == ReminderStatus.CONFIRMED) {
                decide(reminder, Decision.STOP,
                        "User has already CONFIRMED the appointment via a previous reminder. No further action needed.", factors);
                return;
            }

            // User cancelled → stop
            if (last.getStatus() == ReminderStatus.CANCELLED) {
                decide(reminder, Decision.STOP,
                        "Appointment was CANCELLED by user via reminder link.", factors);
                return;
            }

            // User SEEN reminder but no response — check cooldown
            if (last.getStatus() == ReminderStatus.SEEN && last.getActualSentTime() != null) {
                long minutesSinceSent = ChronoUnit.MINUTES.between(last.getActualSentTime(), now);
                if (minutesSinceSent < cooldownMinutes) {
                    long waitMinutes = cooldownMinutes - minutesSinceSent;
                    scheduleWait(reminder, waitMinutes,
                            String.format("Previous reminder was SEEN %d minutes ago but user has not responded. " +
                                            "Cooldown active (%d min). Will re-evaluate in %d minutes.",
                                    minutesSinceSent, cooldownMinutes, waitMinutes), factors);
                    return;
                }
            }

            // Previous send FAILED → recovery
            if (last.getStatus() == ReminderStatus.FAILED) {
                if (last.getAttemptNumber() >= 3) {
                    decide(reminder, Decision.STOP,
                            String.format("Delivery failed %d times. Further retries would be harassment. Stopping.",
                                    last.getAttemptNumber()), factors);
                    return;
                }
                decide(reminder, Decision.RECOVER,
                        String.format("Previous reminder delivery FAILED (attempt %d). Scheduling recovery retry.",
                                last.getAttemptNumber()), factors);
                recoveryService.scheduleRetry(reminder);
                broadcastDecision(reminder, Decision.RECOVER, appointment);
                return;
            }
        }

        // ── Stage 6: Active-Time Window Check ───────────────
        factors.add("ACTIVE_TIME_WINDOW");
        boolean isActiveWindow = activityService.isActiveWindow(appointment.getUser());
        String activeWindowSummary = activityService.getActiveWindowSummary(appointment.getUser());

        // ── Stage 7: Location Check ──────────────────────────
        factors.add("LOCATION_CHECK");
        LocationService.LocationContext locationCtx = locationService.evaluate(appointment);

        // ── Stage 8: Appointment Conflict Check ─────────────
        factors.add("CONFLICT_CHECK");
        boolean hasConflict = conflictService.hasConflict(appointment);

        // ── Stage 9: Final Decision Logic ───────────────────

        // Case: Appointment more than 24 hours away
        if (minutesToAppointment > 24 * 60) {
            if (lastReminderOpt.isEmpty()) {
                if (isActiveWindow) {
                    String reason = String.format(
                            "First reminder. Appointment is %d hours away. " +
                            "User is in an active window. Sending now.\n" +
                            "Active window pattern: %s",
                            minutesToAppointment / 60, activeWindowSummary);
                    executeSend(reminder, reason, factors);
                } else {
                    String reason = String.format(
                            "First reminder. Appointment is %d hours away. " +
                            "User is NOT currently in an active window. " +
                            "Moving reminder to better time.\n" +
                            "Active window pattern: %s",
                            minutesToAppointment / 60, activeWindowSummary);
                    scheduleMove(reminder, 60, reason, factors);
                }
            } else {
                String reason = String.format(
                        "Appointment is %d hours away. A reminder has already been sent. " +
                        "No additional reminder needed this far in advance.",
                        minutesToAppointment / 60);
                scheduleWait(reminder, 120, reason, factors);
            }
            return;
        }

        // Case: Appointment 1–24 hours away
        if (minutesToAppointment > 60) {
            if (locationCtx.isDepartureTimeSoon()) {
                String conflictNote = hasConflict ? " Note: possible travel conflict with another appointment." : "";
                String reason = String.format(
                        "Recommended departure time approaching. " +
                        "Appointment in %d minutes. Travel estimate: %d minutes. %s\n%s",
                        minutesToAppointment, locationCtx.getTravelMinutes(), conflictNote,
                        locationCtx.getReason());
                executeSend(reminder, reason, factors);
                return;
            }

            if (isActiveWindow) {
                String conflictNote = hasConflict ? " Possible conflict with another appointment detected." : "";
                String reason = String.format(
                        "Appointment in %d minutes. User is in an active window. %s\n%s",
                        minutesToAppointment, conflictNote, activeWindowSummary);
                executeSend(reminder, reason, factors);
            } else {
                String reason = String.format(
                        "Appointment in %d minutes. User is NOT in active window. " +
                        "Moving reminder to next active period.\n%s",
                        minutesToAppointment, activeWindowSummary);
                scheduleMove(reminder, 30, reason, factors);
            }
            return;
        }

        // Case: Appointment within 60 minutes — urgent, send regardless
        if (minutesToAppointment > 0) {
            String reason = String.format(
                    "Appointment is IMMINENT in %d minutes. Sending urgent reminder immediately.",
                    minutesToAppointment);
            executeSend(reminder, reason, factors);
        }
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private void executeSend(Reminder reminder, String reason, List<String> factors) {
        decide(reminder, Decision.SEND, reason, factors);
        boolean sent = notificationService.sendEmail(reminder);
        if (!sent && reminder.getStatus() == ReminderStatus.FAILED) {
            log.warn("Notification failed for reminder {}. Recovery will handle it.", reminder.getId());
        }
        broadcastDecision(reminder, Decision.SEND, reminder.getAppointment());
    }

    private void scheduleWait(Reminder reminder, long waitMinutes, String reason, List<String> factors) {
        decide(reminder, Decision.WAIT, reason, factors);
        reminder.setStatus(ReminderStatus.WAITING);
        reminder.setNextEvaluationTime(LocalDateTime.now().plusMinutes(waitMinutes));
        reminderRepository.save(reminder);
        broadcastDecision(reminder, Decision.WAIT, reminder.getAppointment());
    }

    private void scheduleMove(Reminder reminder, long moveMinutes, String reason, List<String> factors) {
        decide(reminder, Decision.MOVE, reason, factors);
        reminder.setStatus(ReminderStatus.WAITING);
        reminder.setPlannedTime(LocalDateTime.now().plusMinutes(moveMinutes));
        reminder.setNextEvaluationTime(LocalDateTime.now().plusMinutes(moveMinutes));
        reminderRepository.save(reminder);
        broadcastDecision(reminder, Decision.MOVE, reminder.getAppointment());
    }

    /**
     * Persist a standard decision to decision_logs.
     */
    private void decide(Reminder reminder, Decision decision, String reason, List<String> factors) {
        String factorsStr = String.join("|", factors);
        DecisionLog dl = new DecisionLog(reminder, decision, reason, factorsStr);
        decisionLogRepository.save(dl);
        this.log.info("Decision [{}] for reminder {} (appointment {}): {}",
                decision, reminder.getId(), reminder.getAppointment().getId(), reason);

        if (decision == Decision.STOP) {
            reminder.setStatus(ReminderStatus.STOPPED);
            reminderRepository.save(reminder);
            reminderEventRepository.save(new ReminderEvent(reminder, EventType.CANCELLED,
                    "Stopped by decision engine: " + reason));
        }
    }

    /**
     * CR-2026/11: Persist a WITHHELD decision.
     *
     * WITHHELD differs from STOP:
     *  - The reminder remains in WAITING state (will be re-evaluated later)
     *  - No ContactAttempt is created
     *  - A DecisionLog records the regulatory block for audit/evidence
     *
     * The reminder is re-scheduled so that once the window clears,
     * it will be evaluated again and a contact can be sent.
     */
    private void decideWithheld(Reminder reminder, String reason, List<String> factors) {
        String factorsStr = String.join("|", factors);
        DecisionLog dl = new DecisionLog(reminder, Decision.WITHHELD, reason, factorsStr);
        decisionLogRepository.save(dl);

        this.log.info("Decision [WITHHELD] for reminder {} (appointment {}): Contact blocked by CR-2026/11 firewall.",
                reminder.getId(), reminder.getAppointment().getId());

        // Keep reminder in WAITING — re-evaluate after 4 hours
        // (Do NOT mark as STOPPED — the appointment still needs a reminder later)
        reminder.setStatus(ReminderStatus.WAITING);
        reminder.setNextEvaluationTime(LocalDateTime.now().plusHours(4));
        reminderRepository.save(reminder);
    }

    /**
     * Push real-time decision update to admin dashboard via WebSocket.
     */
    private void broadcastDecision(Reminder reminder, Decision decision, Appointment appointment) {
        try {
            String message = String.format(
                    "{\"appointmentId\":%d,\"reminderId\":%d,\"decision\":\"%s\",\"time\":\"%s\"}",
                    appointment.getId(), reminder.getId(), decision, LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/admin", message);
        } catch (Exception e) {
            log.debug("WebSocket broadcast failed (non-critical): {}", e.getMessage());
        }
    }
}

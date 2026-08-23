package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.ContactAttempt;
import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.ReminderEvent;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import com.example.SmartRemainderSystem.repository.ReminderEventRepository;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;
    private final ContactLimitService contactLimitService;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Value("${app.mail.from-name:Smart Appointment Reminder}")
    private String fromName;

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy 'at' hh:mm a");

    /**
     * Send an appointment reminder email.
     *
     * CR-2026/11 flow:
     *   1. Validate recipient
     *   2. Create ContactAttempt (PENDING) — BEFORE sending
     *   3. Attempt email dispatch
     *   4. Update ContactAttempt outcome → DELIVERED or FAILED
     *   5. Update Reminder status
     *
     * The ContactAttempt is created before the send so that
     * a failed delivery still counts toward the rolling 7-day limit.
     */
    @Transactional
    public boolean sendEmail(Reminder reminder) {
        Appointment appointment = reminder.getAppointment();
        String recipientEmail = appointment.getUser().getEmail();

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No email address for user {}. Skipping email.", appointment.getUser().getId());
            markFailed(reminder, "No email address configured for user");
            return false;
        }

        String subject = buildSubject(appointment);
        String body = buildEmailBody(reminder, appointment);

        // ── CR-2026/11: Record attempt BEFORE sending ────────
        // This ensures that even if the SMTP call fails, the attempt
        // is already persisted and counts toward the rolling 7-day limit.
        ContactAttempt attempt = contactLimitService.recordAttempt(
                appointment.getUser(), appointment, "EMAIL");
        log.info("ContactAttempt #{} recorded for resident {} before email dispatch.",
                attempt.getId(), appointment.getUser().getId());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromName + " <" + fromEmail + ">");
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            // ── Update ContactAttempt outcome → DELIVERED ────
            contactLimitService.updateOutcome(attempt.getId(), "DELIVERED");

            // Mark reminder as SENT
            reminder.setStatus(ReminderStatus.SENT);
            reminder.setActualSentTime(LocalDateTime.now());
            reminder.setAttemptNumber(reminder.getAttemptNumber() + 1);
            // Schedule next evaluation to check for SEEN/CONFIRMED
            reminder.setNextEvaluationTime(LocalDateTime.now().plusMinutes(15));
            reminderRepository.save(reminder);

            reminderEventRepository.save(
                    new ReminderEvent(reminder, EventType.SENT,
                            "Email sent to " + recipientEmail));

            log.info("Reminder email sent for appointment {} to {}", appointment.getId(), recipientEmail);
            return true;

        } catch (MailException e) {
            // ── Update ContactAttempt outcome → FAILED ───────
            // The attempt already exists in the DB and still counts.
            contactLimitService.updateOutcome(attempt.getId(), "FAILED");

            log.error("Failed to send reminder email for appointment {}: {}", appointment.getId(), e.getMessage());
            markFailed(reminder, "SMTP error: " + e.getMessage());
            return false;
        }
    }

    private void markFailed(Reminder reminder, String reason) {
        reminder.setStatus(ReminderStatus.FAILED);
        reminder.setFailureReason(reason);
        reminder.setAttemptNumber(reminder.getAttemptNumber() + 1);
        reminderRepository.save(reminder);
        reminderEventRepository.save(new ReminderEvent(reminder, EventType.FAILED, reason));
    }

    private String buildSubject(Appointment appointment) {
        return String.format("Reminder: Your appointment at %s — %s",
                appointment.getOfficeName(),
                appointment.getAppointmentTime().toLocalDate());
    }

    private String buildEmailBody(Reminder reminder, Appointment appointment) {
        String userName = appointment.getUser().getName();
        String formattedTime = appointment.getAppointmentTime().format(DISPLAY_FORMAT);
        String baseUrl = "http://localhost:8081";
        String confirmUrl = baseUrl + "/api/reminders/" + reminder.getId() + "/events?type=CONFIRMED";
        String seenUrl = baseUrl + "/api/reminders/" + reminder.getId() + "/events?type=SEEN";

        StringBuilder sb = new StringBuilder();
        sb.append("Hello ").append(userName).append(",\n\n");
        sb.append("This is a reminder for your upcoming appointment:\n\n");
        sb.append("  Office   : ").append(appointment.getOfficeName()).append("\n");
        sb.append("  Service  : ").append(appointment.getService()).append("\n");
        sb.append("  Date/Time: ").append(formattedTime).append("\n");

        if (appointment.getOfficeAddress() != null && !appointment.getOfficeAddress().isBlank()) {
            sb.append("  Address  : ").append(appointment.getOfficeAddress()).append("\n");
        }

        sb.append("\n");
        sb.append("To confirm your attendance, click: ").append(confirmUrl).append("\n\n");
        sb.append("If you have already noted this reminder, click: ").append(seenUrl).append("\n\n");
        sb.append("Please arrive 10-15 minutes early.\n\n");
        sb.append("Smart Appointment Reminder System\n");
        sb.append("(This is an automated message. Do not reply to this email.)");

        return sb.toString();
    }
}

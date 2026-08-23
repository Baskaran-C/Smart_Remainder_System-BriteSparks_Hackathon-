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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
     * Send an appointment reminder email (HTML with Confirm + Seen buttons).
     *
     * CR-2026/11 flow:
     *   1. Validate recipient
     *   2. Create ContactAttempt (PENDING) — BEFORE sending
     *   3. Attempt email dispatch
     *   4. Update ContactAttempt outcome -> DELIVERED or FAILED
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

        String subject  = buildSubject(appointment);
        String htmlBody = buildHtmlEmailBody(reminder, appointment);

        // CR-2026/11: Record attempt BEFORE sending.
        // Even if SMTP fails, the attempt is persisted and counts toward the 7-day limit.
        ContactAttempt attempt = contactLimitService.recordAttempt(
                appointment.getUser(), appointment, "EMAIL");
        log.info("ContactAttempt #{} recorded for resident {} before email dispatch.",
                attempt.getId(), appointment.getUser().getId());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromEmail + ">");
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = isHtml

            mailSender.send(message);

            // Update ContactAttempt outcome -> DELIVERED
            contactLimitService.updateOutcome(attempt.getId(), "DELIVERED");

            // Mark reminder as SENT
            reminder.setStatus(ReminderStatus.SENT);
            reminder.setActualSentTime(LocalDateTime.now());
            reminder.setAttemptNumber(reminder.getAttemptNumber() + 1);
            reminder.setNextEvaluationTime(LocalDateTime.now().plusMinutes(15));
            reminderRepository.save(reminder);

            reminderEventRepository.save(
                    new ReminderEvent(reminder, EventType.SENT,
                            "Email sent to " + recipientEmail));

            log.info("Reminder email sent for appointment {} to {}", appointment.getId(), recipientEmail);
            return true;

        } catch (MailException | MessagingException e) {
            // Update ContactAttempt outcome -> FAILED (attempt still counts toward limit)
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
        return String.format("Reminder: Your appointment at %s - %s",
                appointment.getOfficeName(),
                appointment.getAppointmentTime().toLocalDate());
    }

    private String buildHtmlEmailBody(Reminder reminder, Appointment appointment) {
        String userName      = appointment.getUser().getName();
        String formattedTime = appointment.getAppointmentTime().format(DISPLAY_FORMAT);
        String officeName    = appointment.getOfficeName();
        String service       = appointment.getService();
        String address       = (appointment.getOfficeAddress() != null) ? appointment.getOfficeAddress() : "";

        // Port 8080 — the port the app runs on
        String baseUrl     = "http://localhost:8080";
        String confirmUrl  = baseUrl + "/api/reminders/" + reminder.getId() + "/events?type=CONFIRMED";
        String seenUrl     = baseUrl + "/api/reminders/" + reminder.getId() + "/events?type=SEEN";

        String addressRow = address.isBlank() ? "" :
            "<tr>" +
            "  <td style='padding:6px 0; color:#64748b; font-size:14px; width:130px;'>Address</td>" +
            "  <td style='padding:6px 0; font-size:14px; font-weight:600; color:#1e293b;'>" + address + "</td>" +
            "</tr>";

        return "<!DOCTYPE html>" +
            "<html lang='en'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
            "<title>Appointment Reminder</title></head>" +
            "<body style='margin:0;padding:0;background:#f1f5f9;font-family:Segoe UI,Arial,sans-serif;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f1f5f9;padding:40px 16px;'>" +
            "<tr><td align='center'>" +
            "<table width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;'>" +

            // HEADER
            "<tr><td style='background:linear-gradient(135deg,#4f46e5 0%,#6366f1 100%);" +
            "border-radius:16px 16px 0 0;padding:32px 36px;text-align:center;'>" +
            "<div style='font-size:36px;margin-bottom:10px;'>&#128197;</div>" +
            "<h1 style='margin:0;font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.02em;'>" +
            "Appointment Reminder</h1>" +
            "<p style='margin:8px 0 0;font-size:14px;color:rgba(255,255,255,0.8);'>" +
            "Smart Appointment Reminder System</p>" +
            "</td></tr>" +

            // BODY
            "<tr><td style='background:#ffffff;padding:36px 36px 28px;'>" +
            "<p style='margin:0 0 20px;font-size:16px;color:#1e293b;'>" +
            "Hello <strong>" + userName + "</strong>,</p>" +
            "<p style='margin:0 0 24px;font-size:15px;color:#475569;line-height:1.6;'>" +
            "This is a reminder for your upcoming appointment. Please review the details below and confirm your attendance.</p>" +

            // DETAILS BOX
            "<table width='100%' cellpadding='0' cellspacing='0' " +
            "style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:20px 24px;margin-bottom:28px;'>" +
            "<tr><td colspan='2' style='padding-bottom:12px;border-bottom:1px solid #e2e8f0;'>" +
            "<span style='font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:0.08em;color:#94a3b8;'>" +
            "Appointment Details</span></td></tr>" +
            "<tr>" +
            "  <td style='padding:10px 0 4px;color:#64748b;font-size:14px;width:130px;'>Office</td>" +
            "  <td style='padding:10px 0 4px;font-size:14px;font-weight:700;color:#1e293b;'>" + officeName + "</td>" +
            "</tr>" +
            "<tr>" +
            "  <td style='padding:6px 0;color:#64748b;font-size:14px;'>Service</td>" +
            "  <td style='padding:6px 0;font-size:14px;font-weight:600;color:#1e293b;'>" + service + "</td>" +
            "</tr>" +
            "<tr>" +
            "  <td style='padding:6px 0;color:#64748b;font-size:14px;'>Date &amp; Time</td>" +
            "  <td style='padding:6px 0;font-size:14px;font-weight:700;color:#4f46e5;'>" + formattedTime + "</td>" +
            "</tr>" +
            addressRow +
            "</table>" +

            // CONFIRM BUTTON
            "<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:16px;'>" +
            "<tr><td align='center'>" +
            "<a href='" + confirmUrl + "' " +
            "style='display:inline-block;background:linear-gradient(135deg,#16a34a,#22c55e);" +
            "color:#ffffff;font-size:16px;font-weight:700;text-decoration:none;" +
            "padding:15px 48px;border-radius:10px;letter-spacing:0.01em;'>" +
            "&#9989; Confirm My Appointment" +
            "</a>" +
            "</td></tr></table>" +

            // MARK AS SEEN LINK
            "<p style='text-align:center;margin:0 0 28px;font-size:13px;color:#94a3b8;'>" +
            "Already noted this? &nbsp;" +
            "<a href='" + seenUrl + "' style='color:#6366f1;text-decoration:none;font-weight:600;'>Mark as Seen</a>" +
            "</p>" +

            // WARNING BOX
            "<table width='100%' cellpadding='0' cellspacing='0' " +
            "style='background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:14px 18px;'>" +
            "<tr><td style='font-size:13px;color:#92400e;line-height:1.6;'>" +
            "Please arrive <strong>10-15 minutes early</strong> with all required documents. " +
            "Once you confirm, you will not receive further reminders for this appointment." +
            "</td></tr></table>" +
            "</td></tr>" +

            // FOOTER
            "<tr><td style='background:#f8fafc;border:1px solid #e2e8f0;border-top:none;" +
            "border-radius:0 0 16px 16px;padding:20px 36px;text-align:center;'>" +
            "<p style='margin:0;font-size:12px;color:#94a3b8;line-height:1.6;'>" +
            "This is an automated message from the <strong>Smart Appointment Reminder System</strong>.<br>" +
            "Please do not reply to this email." +
            "</p></td></tr>" +

            "</table></td></tr></table>" +
            "</body></html>";
    }
}

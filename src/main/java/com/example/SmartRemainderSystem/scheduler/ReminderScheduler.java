package com.example.SmartRemainderSystem.scheduler;

import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import com.example.SmartRemainderSystem.service.ReminderDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ReminderScheduler — the heartbeat of the system.
 *
 * Runs on a fixed schedule. Finds all reminders that are due for evaluation
 * and delegates each one to the ReminderDecisionService.
 *
 * The scheduler itself contains NO decision logic.
 * It is purely a trigger mechanism.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final ReminderDecisionService decisionService;

    @Value("${app.reminder.max-retry-attempts:3}")
    private int maxRetryAttempts;

    /**
     * Primary evaluation loop — runs every 60 seconds.
     * Finds reminders with nextEvaluationTime <= now and status SCHEDULED or WAITING.
     */
    @Scheduled(fixedDelayString = "${app.reminder.evaluation-interval-minutes:2}000", initialDelay = 10000)
    public void evaluateDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> dueReminders = reminderRepository.findRemindersNeedingEvaluation(now);

        if (dueReminders.isEmpty()) {
            log.debug("Scheduler tick: no reminders due for evaluation at {}", now);
            return;
        }

        log.info("Scheduler: {} reminder(s) due for evaluation.", dueReminders.size());

        for (Reminder reminder : dueReminders) {
            try {
                decisionService.evaluate(reminder.getId());
            } catch (Exception e) {
                log.error("Error evaluating reminder {}: {}", reminder.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Recovery sweep — runs every 5 minutes.
     * Finds FAILED reminders eligible for recovery.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void sweepFailedReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> failed = reminderRepository.findFailedRemindersForRecovery(maxRetryAttempts, now);

        if (!failed.isEmpty()) {
            log.info("Recovery sweep: {} failed reminder(s) to process.", failed.size());
            for (Reminder reminder : failed) {
                try {
                    decisionService.evaluate(reminder.getId());
                } catch (Exception e) {
                    log.error("Error during recovery for reminder {}: {}", reminder.getId(), e.getMessage(), e);
                }
            }
        }
    }
}

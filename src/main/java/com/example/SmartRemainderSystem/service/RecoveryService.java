package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.ReminderEvent;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import com.example.SmartRemainderSystem.repository.ReminderEventRepository;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {

    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;

    @Value("${app.reminder.max-retry-attempts:3}")
    private int maxRetryAttempts;

    /**
     * Schedule a recovery retry for a failed reminder.
     * Uses exponential back-off: 5 min, 10 min, 20 min.
     */
    @Transactional
    public void scheduleRetry(Reminder reminder) {
        int attempt = reminder.getAttemptNumber();

        if (attempt >= maxRetryAttempts) {
            log.warn("Reminder {} has exceeded max retry attempts ({}). Stopping.", reminder.getId(), maxRetryAttempts);
            reminder.setStatus(ReminderStatus.STOPPED);
            reminder.setFailureReason("Maximum retry attempts (" + maxRetryAttempts + ") reached.");
            reminderRepository.save(reminder);
            reminderEventRepository.save(new ReminderEvent(reminder, EventType.FAILED,
                    "Recovery stopped after " + attempt + " attempts."));
            return;
        }

        // Exponential back-off in minutes: 5, 10, 20
        int delayMinutes = (int) (5 * Math.pow(2, attempt));
        LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(delayMinutes);

        reminder.setStatus(ReminderStatus.WAITING);
        reminder.setNextEvaluationTime(nextRetry);
        reminderRepository.save(reminder);

        reminderEventRepository.save(new ReminderEvent(reminder, EventType.RETRY,
                String.format("Recovery attempt %d scheduled in %d minutes.", attempt + 1, delayMinutes)));

        log.info("Recovery retry scheduled for reminder {} in {} minutes (attempt {}/{})",
                reminder.getId(), delayMinutes, attempt + 1, maxRetryAttempts);
    }

    /**
     * Immediately mark a reminder as unrecoverable (used when STOP is decided).
     */
    @Transactional
    public void stop(Reminder reminder, String reason) {
        reminder.setStatus(ReminderStatus.STOPPED);
        reminder.setFailureReason(reason);
        reminderRepository.save(reminder);
        reminderEventRepository.save(new ReminderEvent(reminder, EventType.FAILED, "Stopped: " + reason));
    }
}

package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.dto.ContactLimitResult;
import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.ContactAttempt;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.repository.ContactAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CR-2026/11 — Rolling 7-Day Contact Limit Service
 *
 * Enforces the regulatory rule:
 *   "A resident can receive a maximum of 2 outbound contact attempts
 *    in any rolling 7-day period, across ALL channels."
 *
 * Key behaviours:
 *  1. Limit is per-resident, not per-appointment or per-channel.
 *  2. Failed outbound attempts count toward the limit.
 *  3. A ContactAttempt is created BEFORE the notification is sent.
 *  4. WITHHELD decisions do NOT create a ContactAttempt.
 *  5. Historical contacts (from before this feature was deployed) count
 *     automatically because the query is time-window based.
 *  6. Rolling window = exactly NOW - 7 × 24 h (not calendar week).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactLimitService {

    private final ContactAttemptRepository contactAttemptRepository;

    @Value("${app.contact-limit.max-per-7-days:2}")
    private int maxContactsPer7Days;

    private static final int ROLLING_WINDOW_DAYS = 7;

    // ─────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────

    /**
     * Check whether the resident is allowed to receive another contact.
     *
     * Uses a pessimistic read lock on the count query to prevent race conditions
     * where two concurrent scheduler threads both read count=1 and both send,
     * resulting in 3 contacts sent against a limit of 2.
     *
     * @param resident the user to check
     * @return ContactLimitResult with allowed/blocked status and full details
     */
    @Transactional
    public ContactLimitResult checkLimit(User resident) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(ROLLING_WINDOW_DAYS);

        long count = contactAttemptRepository.countInWindow(resident, windowStart, now);
        List<ContactAttempt> recentContacts =
                contactAttemptRepository.findInWindow(resident, windowStart);

        log.debug("Contact limit check for resident {}: {}/{} contacts in rolling window [{} → {}]",
                resident.getId(), count, maxContactsPer7Days, windowStart, now);

        if (count < maxContactsPer7Days) {
            return ContactLimitResult.allowed(count, maxContactsPer7Days,
                    windowStart, now, recentContacts, resident.getId());
        }

        // Limit reached — calculate when the oldest attempt falls out of the window
        LocalDateTime nextPermitted = calculateNextPermittedTime(resident, windowStart);

        log.info("Contact limit REACHED for resident {} ({}/{} contacts). Next permitted: {}",
                resident.getId(), count, maxContactsPer7Days, nextPermitted);

        return ContactLimitResult.blocked(count, maxContactsPer7Days,
                windowStart, now, nextPermitted, recentContacts, resident.getId());
    }

    /**
     * Record an outbound contact attempt for a resident.
     *
     * MUST be called BEFORE the notification is dispatched.
     * This guarantees that even failed sends are counted in the rolling window.
     *
     * @param resident    the resident being contacted
     * @param appointment the appointment this contact relates to
     * @param channel     notification channel (EMAIL, SMS, PUSH)
     * @return the saved ContactAttempt (use its ID to update outcome later)
     */
    @Transactional
    public ContactAttempt recordAttempt(User resident, Appointment appointment, String channel) {
        ContactAttempt attempt = new ContactAttempt(resident, appointment, channel);
        ContactAttempt saved = contactAttemptRepository.save(attempt);

        log.info("ContactAttempt #{} created for resident {} / appointment {} via {} [outcome=PENDING]",
                saved.getId(), resident.getId(), appointment.getId(), channel);

        return saved;
    }

    /**
     * Update the outcome of a previously recorded contact attempt.
     *
     * Called by NotificationService after the send succeeds or fails.
     *
     * @param attemptId ID of the ContactAttempt to update
     * @param outcome   "DELIVERED" or "FAILED"
     */
    @Transactional
    public void updateOutcome(Long attemptId, String outcome) {
        contactAttemptRepository.findById(attemptId).ifPresentOrElse(attempt -> {
            attempt.setOutcome(outcome);
            contactAttemptRepository.save(attempt);
            log.info("ContactAttempt #{} outcome updated to {}", attemptId, outcome);
        }, () -> log.warn("ContactAttempt #{} not found when updating outcome to {}", attemptId, outcome));
    }

    /**
     * Fetch all contact attempt data for a resident for the admin dashboard.
     *
     * Returns the ContactLimitResult without modifying any state.
     * Safe to call from read-only API endpoints.
     *
     * @param resident the user to query
     * @return full summary including count, window, history, next permitted time
     */
    @Transactional(readOnly = true)
    public ContactLimitResult getResidentContactSummary(User resident) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(ROLLING_WINDOW_DAYS);

        long count = contactAttemptRepository.countInWindow(resident, windowStart, now);
        List<ContactAttempt> recentContacts =
                contactAttemptRepository.findInWindow(resident, windowStart);

        if (count < maxContactsPer7Days) {
            return ContactLimitResult.allowed(count, maxContactsPer7Days,
                    windowStart, now, recentContacts, resident.getId());
        }

        LocalDateTime nextPermitted = calculateNextPermittedTime(resident, windowStart);
        return ContactLimitResult.blocked(count, maxContactsPer7Days,
                windowStart, now, nextPermitted, recentContacts, resident.getId());
    }

    // ─────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Calculate when the next contact will be permitted.
     *
     * The rolling window slides forward in time. The oldest attempt
     * inside the window will exit the window at:
     *   oldestAttempt.attemptedAt + 7 days
     *
     * At that moment, the count drops from 2 to 1, and a new contact is allowed.
     */
    private LocalDateTime calculateNextPermittedTime(User resident, LocalDateTime windowStart) {
        List<ContactAttempt> oldest =
                contactAttemptRepository.findOldestInWindow(resident, windowStart);

        if (oldest.isEmpty()) {
            // Shouldn't happen if count >= max, but handle gracefully
            return LocalDateTime.now().plusDays(ROLLING_WINDOW_DAYS);
        }

        return oldest.get(0).getAttemptedAt().plusDays(ROLLING_WINDOW_DAYS);
    }
}

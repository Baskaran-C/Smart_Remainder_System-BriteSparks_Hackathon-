package com.example.SmartRemainderSystem.dto;

import com.example.SmartRemainderSystem.entity.ContactAttempt;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CR-2026/11 — Result of the rolling 7-day contact limit check.
 *
 * Returned by ContactLimitService.checkLimit() and used by:
 *  - ReminderDecisionService (to allow or WITHHELD the reminder)
 *  - AdminController (to expose contact limit status via REST API)
 */
@Data
@Builder
public class ContactLimitResult {

    /** True when the resident has NOT yet reached the 2-contact limit. */
    private boolean allowed;

    /** Number of outbound attempts in the current rolling 7-day window. */
    private long currentCount;

    /** Maximum allowed contacts in any 7-day window (configured as 2). */
    private int maximumCount;

    /** Remaining contacts before the limit is reached. */
    private long remainingContacts;

    /** Start of the rolling window = NOW() - 7 days. */
    private LocalDateTime windowStart;

    /** End of the rolling window = NOW(). */
    private LocalDateTime windowEnd;

    /**
     * When the next contact will be permitted.
     * Calculated as: oldestAttemptInWindow.attemptedAt + 7 days.
     * Null when currentCount < maximumCount (i.e., contact is already allowed).
     */
    private LocalDateTime nextPermittedTime;

    /** All contact attempts that fall inside the current rolling window. */
    private List<ContactAttempt> recentContacts;

    /** Resident ID — for logging and API responses. */
    private Long residentId;

    // ─────────────────────────────────────────────────
    // Factory helpers
    // ─────────────────────────────────────────────────

    public static ContactLimitResult allowed(long currentCount, int max,
                                              LocalDateTime windowStart, LocalDateTime windowEnd,
                                              List<ContactAttempt> recentContacts, Long residentId) {
        return ContactLimitResult.builder()
                .allowed(true)
                .currentCount(currentCount)
                .maximumCount(max)
                .remainingContacts(max - currentCount)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .nextPermittedTime(null)
                .recentContacts(recentContacts)
                .residentId(residentId)
                .build();
    }

    public static ContactLimitResult blocked(long currentCount, int max,
                                              LocalDateTime windowStart, LocalDateTime windowEnd,
                                              LocalDateTime nextPermittedTime,
                                              List<ContactAttempt> recentContacts, Long residentId) {
        return ContactLimitResult.builder()
                .allowed(false)
                .currentCount(currentCount)
                .maximumCount(max)
                .remainingContacts(0)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .nextPermittedTime(nextPermittedTime)
                .recentContacts(recentContacts)
                .residentId(residentId)
                .build();
    }
}

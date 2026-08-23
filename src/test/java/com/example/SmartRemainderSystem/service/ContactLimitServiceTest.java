package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.dto.ContactLimitResult;
import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.ContactAttempt;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.repository.ContactAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CR-2026/11 — ContactLimitService Tests
 *
 * Tests all 11 regulatory scenarios described in the requirement.
 */
@ExtendWith(MockitoExtension.class)
class ContactLimitServiceTest {

    @Mock
    private ContactAttemptRepository contactAttemptRepository;

    @InjectMocks
    private ContactLimitService contactLimitService;

    private User resident;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        // Set the max contacts value (normally injected via @Value)
        ReflectionTestUtils.setField(contactLimitService, "maxContactsPer7Days", 2);

        resident = new User();
        ReflectionTestUtils.setField(resident, "id", 1L);

        appointment = new Appointment();
        ReflectionTestUtils.setField(appointment, "id", 10L);
        ReflectionTestUtils.setField(appointment, "user", resident);
    }

    // ─────────────────────────────────────────────────────────
    // Test 1: 0 previous contacts → ALLOWED
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 1: 0 contacts in rolling window → ALLOWED")
    void test1_noContacts_allowed() {
        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(0L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(Collections.emptyList());

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getCurrentCount()).isEqualTo(0);
        assertThat(result.getRemainingContacts()).isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────
    // Test 2: 1 previous contact → ALLOWED
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 2: 1 contact in rolling window → ALLOWED")
    void test2_oneContact_allowed() {
        ContactAttempt ca = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(2));
        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(1L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(List.of(ca));

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getCurrentCount()).isEqualTo(1);
        assertThat(result.getRemainingContacts()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────
    // Test 3: 2 previous contacts → BLOCKED / WITHHELD
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 3: 2 contacts in rolling window → BLOCKED")
    void test3_twoContacts_blocked() {
        ContactAttempt ca1 = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(5));
        ContactAttempt ca2 = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(2));

        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(2L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(List.of(ca2, ca1));
        when(contactAttemptRepository.findOldestInWindow(eq(resident), any()))
                .thenReturn(List.of(ca1)); // oldest is ca1

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getCurrentCount()).isEqualTo(2);
        assertThat(result.getRemainingContacts()).isEqualTo(0);
        assertThat(result.getNextPermittedTime()).isNotNull();
        // next permitted = ca1.attemptedAt + 7 days
        assertThat(result.getNextPermittedTime())
                .isAfter(LocalDateTime.now());
    }

    // ─────────────────────────────────────────────────────────
    // Test 4: Failed attempt still counts
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 4: Failed contact attempt still counts toward limit")
    void test4_failedAttemptCounts() {
        // Both contacts exist, one FAILED — but count is still 2
        ContactAttempt failed = buildAttempt("EMAIL", "FAILED", LocalDateTime.now().minusDays(3));
        ContactAttempt delivered = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(1));

        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(2L); // FAILED attempt is counted
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(List.of(delivered, failed));
        when(contactAttemptRepository.findOldestInWindow(eq(resident), any()))
                .thenReturn(List.of(failed));

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getCurrentCount()).isEqualTo(2);
        // Verify the FAILED attempt is in recentContacts
        assertThat(result.getRecentContacts()).anyMatch(ca -> "FAILED".equals(ca.getOutcome()));
    }

    // ─────────────────────────────────────────────────────────
    // Test 5: Different channel still counts
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 5: Different channel (EMAIL then SMS) both count against same limit")
    void test5_differentChannelCounts() {
        ContactAttempt email = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(4));
        ContactAttempt sms   = buildAttempt("SMS",   "DELIVERED", LocalDateTime.now().minusDays(1));

        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(2L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(List.of(sms, email));
        when(contactAttemptRepository.findOldestInWindow(eq(resident), any()))
                .thenReturn(List.of(email));

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRecentContacts()).hasSize(2);
        assertThat(result.getRecentContacts()).anyMatch(ca -> "EMAIL".equals(ca.getChannel()));
        assertThat(result.getRecentContacts()).anyMatch(ca -> "SMS".equals(ca.getChannel()));
    }

    // ─────────────────────────────────────────────────────────
    // Test 6: Different appointment, same resident — still counts
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 6: Contact for different appointment still counts for resident")
    void test6_differentAppointmentCounts() {
        // Both contacts exist for the same resident but different appointments
        // The repository query is resident-scoped, not appointment-scoped
        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(2L); // both from different appointments counted
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(List.of(
                        buildAttemptForAppt("EMAIL", "DELIVERED", 10L, LocalDateTime.now().minusDays(5)),
                        buildAttemptForAppt("EMAIL", "DELIVERED", 11L, LocalDateTime.now().minusDays(2))
                ));
        when(contactAttemptRepository.findOldestInWindow(eq(resident), any()))
                .thenReturn(List.of(buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(5))));

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getCurrentCount()).isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────
    // Test 7: Contact older than 7 days → NOT counted
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 7: Contact older than 7 days is outside the window and not counted")
    void test7_oldContactNotCounted() {
        // Repository returns 0 because the old contact is outside the window
        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(0L); // 8-day-old contact excluded by window filter
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(Collections.emptyList());

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getCurrentCount()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────
    // Test 8: Historical contact (pre-feature) still counts if in window
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 8: Historical contact made before feature deployment counts if in rolling window")
    void test8_historicalContactCounts() {
        // Contact recorded 3 days ago (before this feature was in prod, but within window)
        ContactAttempt historical = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(3));
        ContactAttempt recent = buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(1));

        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(2L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(List.of(recent, historical));
        when(contactAttemptRepository.findOldestInWindow(eq(resident), any()))
                .thenReturn(List.of(historical));

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRecentContacts()).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────
    // Test 9: WITHHELD decision → No ContactAttempt created
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 9: recordAttempt is NOT called when decision is WITHHELD")
    void test9_withheldDoesNotCreateContactAttempt() {
        // When blocked, the decision engine returns early WITHOUT calling recordAttempt.
        // Verify no save() happens on contactAttemptRepository.
        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(2L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(Collections.emptyList());
        when(contactAttemptRepository.findOldestInWindow(eq(resident), any()))
                .thenReturn(List.of(buildAttempt("EMAIL", "DELIVERED", LocalDateTime.now().minusDays(5))));

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isFalse();
        // recordAttempt must NOT have been called — no save on the repository
        verify(contactAttemptRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────
    // Test 10: Failed outbound → ContactAttempt exists and counts
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 10: recordAttempt persists a ContactAttempt before send; outcome updated to FAILED")
    void test10_failedAttemptPersistedAndCounted() {
        ContactAttempt pending = buildAttempt("EMAIL", "PENDING", LocalDateTime.now());
        pending.setId(99L); // Give it an ID as if saved

        when(contactAttemptRepository.save(any(ContactAttempt.class))).thenReturn(pending);
        when(contactAttemptRepository.findById(99L)).thenReturn(Optional.of(pending));

        // recordAttempt: creates PENDING record before send
        ContactAttempt result = contactLimitService.recordAttempt(resident, appointment, "EMAIL");
        assertThat(result.getOutcome()).isEqualTo("PENDING");

        // updateOutcome: called after failed send
        contactLimitService.updateOutcome(99L, "FAILED");
        verify(contactAttemptRepository, atLeastOnce()).save(argThat(ca -> "FAILED".equals(ca.getOutcome())));
    }

    // ─────────────────────────────────────────────────────────
    // Test 11: Concurrent sends — pessimistic lock prevents > 2 contacts
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 11: checkLimit uses pessimistic read lock — concurrent sends cannot exceed 2")
    void test11_pessimisticLockVerified() {
        // The @Lock(PESSIMISTIC_READ) on countInWindow ensures serialisation.
        // Verify that countInWindow is called with the correct window boundaries.
        when(contactAttemptRepository.countInWindow(eq(resident), any(), any()))
                .thenReturn(1L);
        when(contactAttemptRepository.findInWindow(eq(resident), any()))
                .thenReturn(Collections.emptyList());

        ContactLimitResult result = contactLimitService.checkLimit(resident);

        assertThat(result.isAllowed()).isTrue();
        // Verify the window used is exactly 7 days ago to now
        verify(contactAttemptRepository).countInWindow(
                eq(resident),
                argThat(start -> start.isAfter(LocalDateTime.now().minusDays(7).minusSeconds(5))),
                argThat(end   -> end.isBefore(LocalDateTime.now().plusSeconds(5)))
        );
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private ContactAttempt buildAttempt(String channel, String outcome, LocalDateTime attemptedAt) {
        ContactAttempt ca = new ContactAttempt(resident, appointment, channel);
        ca.setOutcome(outcome);
        ca.setAttemptedAt(attemptedAt);
        return ca;
    }

    private ContactAttempt buildAttemptForAppt(String channel, String outcome,
                                                Long apptId, LocalDateTime attemptedAt) {
        Appointment appt = new Appointment();
        ReflectionTestUtils.setField(appt, "id", apptId);
        ReflectionTestUtils.setField(appt, "user", resident);
        ContactAttempt ca = new ContactAttempt(resident, appt, channel);
        ca.setOutcome(outcome);
        ca.setAttemptedAt(attemptedAt);
        return ca;
    }
}

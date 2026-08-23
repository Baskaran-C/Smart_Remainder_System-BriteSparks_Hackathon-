package com.example.SmartRemainderSystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CR-2026/11 — Rolling 7-Day Contact Limit
 *
 * Represents ONE outbound contact attempt made to a resident.
 *
 * IMPORTANT RULES:
 *  - This record is created BEFORE the notification is dispatched.
 *  - Failed attempts still count toward the 7-day limit.
 *  - WITHHELD decisions do NOT create a ContactAttempt.
 *  - The limit is per resident, not per appointment or channel.
 */
@Entity
@Table(name = "contact_attempts",
       indexes = {
           @Index(name = "idx_contact_attempts_resident_time",
                  columnList = "resident_id, attempted_at")
       })
@Data
@NoArgsConstructor
public class ContactAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The resident (user) being contacted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private User resident;

    /** Which appointment this contact relates to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    /** Notification channel used for this attempt. */
    @Column(nullable = false, length = 20)
    private String channel = "EMAIL";

    /**
     * Timestamp of the contact attempt.
     * Set to NOW() BEFORE the notification is dispatched.
     * This ensures failed attempts are counted in the rolling window.
     */
    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    /**
     * Outcome of the attempt.
     * PENDING  → set at creation (before send)
     * DELIVERED → updated after successful send
     * FAILED    → updated after send failure
     */
    @Column(nullable = false, length = 20)
    private String outcome = "PENDING";

    public ContactAttempt(User resident, Appointment appointment, String channel) {
        this.resident = resident;
        this.appointment = appointment;
        this.channel = channel;
        this.attemptedAt = LocalDateTime.now();
        this.outcome = "PENDING";
    }
}

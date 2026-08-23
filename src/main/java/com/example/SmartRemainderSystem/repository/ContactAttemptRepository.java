package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.ContactAttempt;
import com.example.SmartRemainderSystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CR-2026/11 — Repository for contact attempt tracking.
 *
 * Core query: count actual outbound attempts for a resident
 * within the rolling 7-day window (attempted_at >= NOW - 7 days).
 */
@Repository
public interface ContactAttemptRepository extends JpaRepository<ContactAttempt, Long> {

    /**
     * Count outbound contact attempts for a resident inside the rolling window.
     * Used by the Contact Limit Firewall in the decision engine.
     *
     * Uses pessimistic read lock to protect against race conditions
     * where two scheduler threads both see count=1 and both send.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT COUNT(ca) FROM ContactAttempt ca " +
           "WHERE ca.resident = :resident " +
           "AND ca.attemptedAt >= :windowStart " +
           "AND ca.attemptedAt <= :windowEnd")
    long countInWindow(@Param("resident") User resident,
                       @Param("windowStart") LocalDateTime windowStart,
                       @Param("windowEnd") LocalDateTime windowEnd);

    /**
     * Fetch all attempts for a resident in the rolling window, newest first.
     * Used for admin dashboard display and next-permitted-time calculation.
     */
    @Query("SELECT ca FROM ContactAttempt ca " +
           "WHERE ca.resident = :resident " +
           "AND ca.attemptedAt >= :windowStart " +
           "ORDER BY ca.attemptedAt DESC")
    List<ContactAttempt> findInWindow(@Param("resident") User resident,
                                      @Param("windowStart") LocalDateTime windowStart);

    /**
     * Fetch all contact attempts for a resident, newest first.
     * Used by admin panel to display full contact history per resident.
     */
    List<ContactAttempt> findByResidentOrderByAttemptedAtDesc(User resident);

    /**
     * Find the oldest attempt in the rolling window.
     * Used to calculate when the next contact will be permitted
     * (oldest attempt_at + 7 days = next permitted time).
     */
    @Query("SELECT ca FROM ContactAttempt ca " +
           "WHERE ca.resident = :resident " +
           "AND ca.attemptedAt >= :windowStart " +
           "ORDER BY ca.attemptedAt ASC")
    List<ContactAttempt> findOldestInWindow(@Param("resident") User resident,
                                             @Param("windowStart") LocalDateTime windowStart);
}

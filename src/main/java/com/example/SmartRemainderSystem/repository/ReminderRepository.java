package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByAppointment(Appointment appointment);

    List<Reminder> findByAppointmentOrderByCreatedAtAsc(Appointment appointment);

    Optional<Reminder> findTopByAppointmentOrderByCreatedAtDesc(Appointment appointment);

    // Scheduler query: find reminders that need evaluation right now
    @Query("SELECT r FROM Reminder r WHERE r.status IN ('SCHEDULED', 'WAITING') " +
           "AND r.nextEvaluationTime <= :now")
    List<Reminder> findRemindersNeedingEvaluation(@Param("now") LocalDateTime now);

    // Count sent/delivered/seen/confirmed reminders per appointment (anti-harassment)
    @Query("SELECT COUNT(r) FROM Reminder r WHERE r.appointment = :appointment " +
           "AND r.status IN ('SENT', 'DELIVERED', 'SEEN', 'CONFIRMED')")
    long countSentReminders(@Param("appointment") Appointment appointment);

    // Count ALL reminders for an appointment regardless of status
    long countByAppointment(Appointment appointment);

    // For analytics
    long countByStatus(ReminderStatus status);

    // Find FAILED reminders eligible for recovery
    @Query("SELECT r FROM Reminder r WHERE r.status = 'FAILED' " +
           "AND r.attemptNumber < :maxAttempts " +
           "AND (r.nextEvaluationTime IS NULL OR r.nextEvaluationTime <= :now)")
    List<Reminder> findFailedRemindersForRecovery(@Param("maxAttempts") int maxAttempts,
                                                   @Param("now") LocalDateTime now);

    // Check if appointment already has an active (non-terminal) reminder
    @Query("SELECT COUNT(r) > 0 FROM Reminder r WHERE r.appointment = :appointment " +
           "AND r.status IN ('SCHEDULED', 'WAITING', 'SENT')")
    boolean hasActiveReminder(@Param("appointment") Appointment appointment);
}

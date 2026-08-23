package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.DecisionLog;
import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.enums.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionLogRepository extends JpaRepository<DecisionLog, Long> {

    List<DecisionLog> findByReminderOrderByCreatedAtAsc(Reminder reminder);

    // All decision logs for an appointment (across all its reminders)
    @Query("SELECT dl FROM DecisionLog dl WHERE dl.reminder.appointment.id = :appointmentId " +
           "ORDER BY dl.createdAt ASC")
    List<DecisionLog> findByAppointmentId(@Param("appointmentId") Long appointmentId);

    long countByDecision(Decision decision);
}

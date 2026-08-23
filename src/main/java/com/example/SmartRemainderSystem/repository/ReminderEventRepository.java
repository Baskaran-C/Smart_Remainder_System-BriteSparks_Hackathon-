package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.Reminder;
import com.example.SmartRemainderSystem.entity.ReminderEvent;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderEventRepository extends JpaRepository<ReminderEvent, Long> {

    List<ReminderEvent> findByReminderOrderByEventTimeAsc(Reminder reminder);

    Optional<ReminderEvent> findTopByReminderAndEventTypeOrderByEventTimeDesc(
            Reminder reminder, EventType eventType);

    boolean existsByReminderAndEventType(Reminder reminder, EventType eventType);

    long countByEventType(EventType eventType);
}

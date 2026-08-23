package com.example.SmartRemainderSystem.entity;

import com.example.SmartRemainderSystem.entity.enums.EventType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_events")
@Data
@NoArgsConstructor
public class ReminderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reminder_id", nullable = false)
    private Reminder reminder;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    // Optional detail about the event (e.g., failure reason, response text)
    @Column(name = "detail", length = 500)
    private String detail;

    public ReminderEvent(Reminder reminder, EventType eventType, String detail) {
        this.reminder = reminder;
        this.eventType = eventType;
        this.eventTime = LocalDateTime.now();
        this.detail = detail;
    }
}

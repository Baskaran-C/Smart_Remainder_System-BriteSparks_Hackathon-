package com.example.SmartRemainderSystem.entity;

import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reminders")
@Data
@NoArgsConstructor
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    // When we originally planned to send this reminder
    @Column(name = "planned_time")
    private LocalDateTime plannedTime;

    // When we actually sent it (null until sent)
    @Column(name = "actual_sent_time")
    private LocalDateTime actualSentTime;

    // Current lifecycle status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.SCHEDULED;

    // How many delivery attempts have been made
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 0;

    // When the scheduler should next evaluate this reminder
    @Column(name = "next_evaluation_time")
    private LocalDateTime nextEvaluationTime;

    // Last delivery failure reason (for recovery logic)
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "reminder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReminderEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "reminder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DecisionLog> decisionLogs = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

package com.example.SmartRemainderSystem.entity;

import com.example.SmartRemainderSystem.entity.enums.Decision;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "decision_logs")
@Data
@NoArgsConstructor
public class DecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reminder_id", nullable = false)
    private Reminder reminder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    // Human-readable explanation of why this decision was made (for admin visibility)
    @Column(nullable = false, length = 1000)
    private String reason;

    // The factors that were checked (pipe-separated, e.g. "TIME_CHECK|HARASSMENT_FIREWALL|ACTIVE_WINDOW")
    @Column(name = "factors_checked", length = 500)
    private String factorsChecked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DecisionLog(Reminder reminder, Decision decision, String reason, String factorsChecked) {
        this.reminder = reminder;
        this.decision = decision;
        this.reason = reason;
        this.factorsChecked = factorsChecked;
        this.createdAt = LocalDateTime.now();
    }
}

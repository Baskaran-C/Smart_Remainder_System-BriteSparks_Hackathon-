package com.example.SmartRemainderSystem.entity;

import com.example.SmartRemainderSystem.entity.enums.ActivityType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_activity")
@Data
@NoArgsConstructor
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;

    @Column(name = "activity_time", nullable = false)
    private LocalDateTime activityTime;

    // Optional: related appointment/reminder id for context
    @Column(name = "reference_id")
    private Long referenceId;

    public UserActivity(User user, ActivityType activityType) {
        this.user = user;
        this.activityType = activityType;
        this.activityTime = LocalDateTime.now();
    }

    public UserActivity(User user, ActivityType activityType, Long referenceId) {
        this.user = user;
        this.activityType = activityType;
        this.activityTime = LocalDateTime.now();
        this.referenceId = referenceId;
    }
}

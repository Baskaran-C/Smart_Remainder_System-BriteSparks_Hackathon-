package com.example.SmartRemainderSystem.dto;

import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReminderResponse {
    private Long id;
    private Long appointmentId;
    private LocalDateTime plannedTime;
    private LocalDateTime actualSentTime;
    private ReminderStatus status;
    private int attemptNumber;
    private LocalDateTime nextEvaluationTime;
    private String failureReason;
    private LocalDateTime createdAt;
    private List<ReminderEventResponse> events;
    private List<DecisionResponse> decisions;

    @Data
    public static class ReminderEventResponse {
        private Long id;
        private String eventType;
        private LocalDateTime eventTime;
        private String detail;
    }
}

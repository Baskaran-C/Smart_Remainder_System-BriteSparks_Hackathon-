package com.example.SmartRemainderSystem.dto;

import com.example.SmartRemainderSystem.entity.enums.Decision;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DecisionResponse {
    private Long id;
    private Long reminderId;
    private Decision decision;
    private String reason;
    private String factorsChecked;
    private LocalDateTime createdAt;
}

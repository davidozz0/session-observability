package com.davidozzo.demo.observability.model.dto;

import java.time.LocalDateTime;

public class SessionSummary {

    private String sessionId;
    private LocalDateTime createdAt;

    public SessionSummary() {
    }

    public SessionSummary(String sessionId, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

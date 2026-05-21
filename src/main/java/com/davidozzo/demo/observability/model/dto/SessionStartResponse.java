package com.davidozzo.demo.observability.model.dto;

public class SessionStartResponse {

    private String sessionId;

    public SessionStartResponse() {
    }

    public SessionStartResponse(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}

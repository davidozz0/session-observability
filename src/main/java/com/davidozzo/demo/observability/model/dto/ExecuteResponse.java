package com.davidozzo.demo.observability.model.dto;

public class ExecuteResponse {

    private String status;

    public ExecuteResponse() {
    }

    public ExecuteResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}

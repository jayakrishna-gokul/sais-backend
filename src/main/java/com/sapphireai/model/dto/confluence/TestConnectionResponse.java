package com.sapphireai.model.dto.confluence;

import java.time.LocalDateTime;

/**
 * DTO for test connection response.
 */
public class TestConnectionResponse {

    private String connectionId;
    private boolean success;
    private String message;
    private LocalDateTime testedAt;

    // Constructors
    public TestConnectionResponse() {
    }

    // Getters and Setters
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTestedAt() {
        return testedAt;
    }

    public void setTestedAt(LocalDateTime testedAt) {
        this.testedAt = testedAt;
    }
}
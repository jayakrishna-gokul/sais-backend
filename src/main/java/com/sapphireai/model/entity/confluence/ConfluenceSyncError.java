package com.sapphireai.model.entity.confluence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a sync error during Confluence synchronization.
 */
@Entity
@Table(name = "confluence_sync_error",
        indexes = {
                @Index(name = "idx_confluence_sync_errors_job", columnList = "job_id"),
                @Index(name = "idx_confluence_sync_errors_connection", columnList = "connection_id")
        })
public class ConfluenceSyncError {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType; // SPACE, PAGE, ATTACHMENT

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "error_type", nullable = false, length = 100)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // Constructors
    public ConfluenceSyncError() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
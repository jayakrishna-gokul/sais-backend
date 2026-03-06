package com.sapphireai.model.dto.confluence;

import java.time.LocalDateTime;

/**
 * DTO for sync job response.
 */
public class SyncJobResponse {

    private String id;
    private String connectionId;
    private String organizationId;
    private String jobType;
    private String status;
    private int processedSpaces;
    private int processedPages;
    private int processedAttachments;
    private int errorCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    // Constructors
    public SyncJobResponse() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProcessedSpaces() {
        return processedSpaces;
    }

    public void setProcessedSpaces(int processedSpaces) {
        this.processedSpaces = processedSpaces;
    }

    public int getProcessedPages() {
        return processedPages;
    }

    public void setProcessedPages(int processedPages) {
        this.processedPages = processedPages;
    }

    public int getProcessedAttachments() {
        return processedAttachments;
    }

    public void setProcessedAttachments(int processedAttachments) {
        this.processedAttachments = processedAttachments;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
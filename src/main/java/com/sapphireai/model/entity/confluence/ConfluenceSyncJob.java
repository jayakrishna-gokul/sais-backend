package com.sapphireai.model.entity.confluence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a Confluence sync job.
 */
@Entity
@Table(name = "confluence_sync_job")
public class ConfluenceSyncJob {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(name = "job_type", nullable = false, length = 20)
    private String jobType; // FULL, INCREMENTAL

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED

    @Column(name = "processed_spaces")
    private int processedSpaces = 0;

    @Column(name = "processed_pages")
    private int processedPages = 0;

    @Column(name = "processed_attachments")
    private int processedAttachments = 0;

    @Column(name = "error_count")
    private int errorCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public ConfluenceSyncJob() {
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
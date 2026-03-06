package com.sapphireai.model.dto.confluence;

import java.time.LocalDateTime;

/**
 * DTO for sync statistics response.
 */
public class SyncStatsResponse {

    private String connectionId;
    private long totalPages;
    private long totalAttachments;
    private int successfulSyncs;
    private int failedSyncs;
    private LocalDateTime lastSyncAt;

    // Constructors
    public SyncStatsResponse() {
    }

    // Getters and Setters
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public long getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(long totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotalAttachments() {
        return totalAttachments;
    }

    public void setTotalAttachments(long totalAttachments) {
        this.totalAttachments = totalAttachments;
    }

    public int getSuccessfulSyncs() {
        return successfulSyncs;
    }

    public void setSuccessfulSyncs(int successfulSyncs) {
        this.successfulSyncs = successfulSyncs;
    }

    public int getFailedSyncs() {
        return failedSyncs;
    }

    public void setFailedSyncs(int failedSyncs) {
        this.failedSyncs = failedSyncs;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
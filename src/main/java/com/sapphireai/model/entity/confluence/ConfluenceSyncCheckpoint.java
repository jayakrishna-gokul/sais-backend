package com.sapphireai.model.entity.confluence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a sync checkpoint for resumable syncs.
 */
@Entity
@Table(name = "confluence_sync_checkpoint",
        indexes = {
                @Index(name = "idx_confluence_checkpoints_job", columnList = "job_id"),
                @Index(name = "idx_confluence_checkpoints_connection", columnList = "connection_id")
        })
public class ConfluenceSyncCheckpoint {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(name = "space_key", length = 100)
    private String spaceKey;

    @Column(name = "last_synced_page_id", length = 100)
    private String lastSyncedPageId;

    @Column(name = "checkpoint_data", columnDefinition = "TEXT")
    private String checkpointData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public ConfluenceSyncCheckpoint() {
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

    public String getSpaceKey() {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getLastSyncedPageId() {
        return lastSyncedPageId;
    }

    public void setLastSyncedPageId(String lastSyncedPageId) {
        this.lastSyncedPageId = lastSyncedPageId;
    }

    public String getCheckpointData() {
        return checkpointData;
    }

    public void setCheckpointData(String checkpointData) {
        this.checkpointData = checkpointData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
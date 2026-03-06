package com.sapphireai.model.entity.confluence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a synced Confluence page.
 */
@Entity
@Table(name = "confluence_page",
        indexes = {
                @Index(name = "idx_confluence_pages_connection", columnList = "connection_id"),
                @Index(name = "idx_confluence_pages_space", columnList = "connection_id,space_key"),
                @Index(name = "idx_confluence_pages_page_id", columnList = "connection_id,page_id")
        })
public class ConfluencePage {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;

    @Column(name = "space_key", nullable = false, length = 100)
    private String spaceKey;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "last_modified_by", length = 255)
    private String lastModifiedBy;

    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public ConfluencePage() {
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

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getSpaceKey() {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(LocalDateTime lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
package com.sapphireai.model.entity.confluence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a Confluence page attachment.
 */
@Entity
@Table(name = "confluence_attachment",
        indexes = {
                @Index(name = "idx_confluence_attachments_connection", columnList = "connection_id"),
                @Index(name = "idx_confluence_attachments_page", columnList = "confluence_page_id")
        })
public class ConfluenceAttachment {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(name = "confluence_page_id", nullable = false, length = 100)
    private String pageId;

    @Column(name = "confluence_attachment_id", nullable = false, length = 100)
    private String attachmentId;

    @Column(name = "confluence_file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "media_type", length = 100)
    private String mediaType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "download_url", length = 1000)
    private String downloadUrl;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public ConfluenceAttachment() {
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

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
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
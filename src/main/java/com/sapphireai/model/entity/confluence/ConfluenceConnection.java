package com.sapphireai.model.entity.confluence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing a Confluence connection configuration.
 */
@Entity
@Table(name = "confluence_connection")
public class ConfluenceConnection {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 50)
    private String organizationId;

    @Column(name = "connection_name", length = 255)
    private String connectionName;

    @Column(name = "instance_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "api_token", columnDefinition = "TEXT")
    private String apiToken;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "confluence_connection_space",
            joinColumns = @JoinColumn(name = "connection_id"))
    @Column(name = "space_key")
    private List<String> spaceKeys;

    @Column(name = "sync_enabled")
    private boolean enabled = true;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public ConfluenceConnection() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public List<String> getSpaceKeys() {
        return spaceKeys;
    }

    public void setSpaceKeys(List<String> spaceKeys) {
        this.spaceKeys = spaceKeys;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
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
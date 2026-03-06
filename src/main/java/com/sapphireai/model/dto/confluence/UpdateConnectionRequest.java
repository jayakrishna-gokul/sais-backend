package com.sapphireai.model.dto.confluence;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO for updating a Confluence connection.
 */
public class UpdateConnectionRequest {

    @Size(max = 255)
    private String connectionName;

    @Size(max = 255)
    private String username;

    @Size(max = 500)
    private String apiToken;

    private List<String> spaceKeys;

    private Boolean enabled;

    // Constructors
    public UpdateConnectionRequest() {
    }

    // Getters and Setters
    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
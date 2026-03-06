package com.sapphireai.model.dto.confluence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO for creating a new Confluence connection.
 */
public class CreateConnectionRequest {

    @NotBlank(message = "Base URL is required")
    @Size(max = 500)
    private String baseUrl;

    //@NotBlank(message = "Username is required")
    @Size(max = 255)
    private String username;

    //@NotBlank(message = "API token is required")
    @Size(max = 500)
    private String apiToken;

    @NotBlank(message = "Connection name is required")
    @Size(max = 255)
    private String connectionName;

    private List<String> spaceKeys;

    // Constructors
    public CreateConnectionRequest() {
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

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public List<String> getSpaceKeys() {
        return spaceKeys;
    }

    public void setSpaceKeys(List<String> spaceKeys) {
        this.spaceKeys = spaceKeys;
    }
}
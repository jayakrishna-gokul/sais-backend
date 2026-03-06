package com.sapphireai.model.dto.confluence;

import java.util.List;

/**
 * DTO for starting a sync operation with optional filters.
 */
public class StartSyncRequest {

    private List<String> spaceKeys;
    private boolean forceFullSync = false;

    // Constructors
    public StartSyncRequest() {
    }

    // Getters and Setters
    public List<String> getSpaceKeys() {
        return spaceKeys;
    }

    public void setSpaceKeys(List<String> spaceKeys) {
        this.spaceKeys = spaceKeys;
    }

    public boolean isForceFullSync() {
        return forceFullSync;
    }

    public void setForceFullSync(boolean forceFullSync) {
        this.forceFullSync = forceFullSync;
    }
}
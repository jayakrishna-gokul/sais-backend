package com.sapphireai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalDocuments;
    private long totalChunks;
    private long totalUsers;
    private long totalSearchQueries;
    private long totalAuditLogs;
    private ConnectorStats confluence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectorStats {
        private boolean connected;
        private long totalPages;
        private String lastSyncedAt;     // null if never synced
        private String status;           // SYNCED, SYNCING, FAILED, NEVER_SYNCED
    }
}
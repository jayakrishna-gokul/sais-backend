package com.sapphireai.service.admin;

import com.sapphireai.model.dto.AdminStatsResponse;
import com.sapphireai.model.entity.confluence.ConfluenceConnection;
import com.sapphireai.repository.ChunkRepository;
import com.sapphireai.repository.DocumentRepository;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.repository.confluence.ConfluenceConnectionRepository;
import com.sapphireai.repository.confluence.ConfluencePageRepository;
import com.sapphireai.repository.audit.AuditLogRepository;
import com.sapphireai.repository.ConversationTurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStatsService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final UserRepository userRepository;
    private final ConversationTurnRepository conversationTurnRepository;
    private final AuditLogRepository auditLogRepository;
    private final ConfluencePageRepository confluencePageRepository;
    private final ConfluenceConnectionRepository confluenceConnectionRepository;  // ← actual class

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats(String organizationId) {
        long totalDocuments = documentRepository.countByOrganizationId(organizationId);
        long totalChunks = chunkRepository.countByOrganizationId(organizationId);
        long totalUsers = userRepository.countByOrganizationId(organizationId);
        long totalSearchQueries = conversationTurnRepository.countByOrganizationId(organizationId);
        long totalAuditLogs = auditLogRepository.countByOrganizationId(organizationId);

        AdminStatsResponse.ConnectorStats confluenceStats = getConfluenceStats(organizationId);

        return AdminStatsResponse.builder()
                .totalDocuments(totalDocuments)
                .totalChunks(totalChunks)
                .totalUsers(totalUsers)
                .totalSearchQueries(totalSearchQueries)
                .totalAuditLogs(totalAuditLogs)
                .confluence(confluenceStats)
                .build();
    }

    private AdminStatsResponse.ConnectorStats getConfluenceStats(String organizationId) {
        // Get ALL connections for this organization (supports multiple Confluence instances)
        List<ConfluenceConnection> connections =
                confluenceConnectionRepository.findByOrganizationId(organizationId);

        if (connections.isEmpty()) {
            return AdminStatsResponse.ConnectorStats.builder()
                    .connected(false)
                    .totalPages(0)
                    .lastSyncedAt(null)
                    .status("NEVER_SYNCED")
                    .build();
        }

        // Aggregate stats across ALL connections
        long totalPages = connections.stream()
                .mapToLong(conn -> confluencePageRepository.countByConnectionId(conn.getId()))
                .sum();

        // Find the most recent sync across all connections using connection.last_sync_at
        Optional<LocalDateTime> lastSynced = connections.stream()
                .map(ConfluenceConnection::getLastSyncAt)
                .filter(syncTime -> syncTime != null)
                .max(LocalDateTime::compareTo);

        String lastSyncedAt = lastSynced
                .map(ldt -> DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").format(ldt))
                .orElse(null);

        String status = lastSyncedAt != null ? "SYNCED" : "NEVER_SYNCED";

        return AdminStatsResponse.ConnectorStats.builder()
                .connected(true)
                .totalPages(totalPages)
                .lastSyncedAt(lastSyncedAt)
                .status(status)
                .build();
    }
}
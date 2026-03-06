package com.sapphireai.controller;

import com.sapphireai.model.entity.User;
import com.sapphireai.model.dto.AdminStatsResponse;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.service.admin.AdminStatsService;
import com.sapphireai.service.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final VectorStoreService vectorStoreService;
    private final JdbcTemplate jdbcTemplate;
    private final AdminStatsService adminStatsService;
    private final UserRepository userRepository;

    @PostMapping("/reset-vectors")
    public ResponseEntity<String> resetVectors() {
        vectorStoreService.deleteCollection();
        return ResponseEntity.ok("Collection reset");
    }

    @PostMapping("/cleanup-test-data")
    public ResponseEntity<Map<String, Object>> cleanupTestData(
            @RequestParam String connectionId) {

        try {
            log.info("Cleaning up test data for connection: {}", connectionId);

            // Delete chunks
            int chunksDeleted = jdbcTemplate.update(
                    "DELETE FROM chunks WHERE document_id IN " +
                            "(SELECT id FROM documents WHERE id LIKE ?)",
                    "confluence_" + connectionId + "%"
            );

            // Delete documents
            int docsDeleted = jdbcTemplate.update(
                    "DELETE FROM documents WHERE id LIKE ?",
                    "confluence_" + connectionId + "%"
            );

            // Delete pages
            int pagesDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_pages WHERE connection_id = ?",
                    connectionId
            );

            // Delete attachments
            int attachmentsDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_attachments WHERE connection_id = ?",
                    connectionId
            );

            // Delete jobs
            int jobsDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_sync_jobs WHERE connection_id = ?",
                    connectionId
            );

            // Delete connection
            int connDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_connections WHERE id = ?",
                    connectionId
            );

            // Reset Qdrant
            vectorStoreService.deleteCollection();

            log.info("Clean-up complete: docs={}, chunks={}, pages={}, attachments={}, jobs={}, conn={}",
                    docsDeleted, chunksDeleted, pagesDeleted, attachmentsDeleted, jobsDeleted, connDeleted);

            return ResponseEntity.ok(Map.of(
                    "message", "Test data cleaned up successfully",
                    "documentsDeleted", docsDeleted,
                    "chunksDeleted", chunksDeleted,
                    "pagesDeleted", pagesDeleted,
                    "attachmentsDeleted", attachmentsDeleted,
                    "jobsDeleted", jobsDeleted,
                    "connectionDeleted", connDeleted
            ));

        } catch (Exception e) {
            log.error("Clean-up failed", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        AdminStatsResponse stats = adminStatsService.getStats(user.getOrganizationId());
        return ResponseEntity.ok(stats);
    }
}

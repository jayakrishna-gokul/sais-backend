package com.sapphireai.controller;

import com.sapphireai.model.dto.confluence.*;
import com.sapphireai.model.entity.User;
import com.sapphireai.model.entity.confluence.*;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.service.confluence.ConfluenceSyncService;
import com.sapphireai.service.confluence.ConfluenceApiClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for Confluence connector operations.
 * Provides endpoints for connection management, sync operations, and monitoring.
 */
@RestController
@RequestMapping("/api/confluence")
public class ConfluenceConnectorController {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceConnectorController.class);

    @Autowired
    private ConfluenceSyncService syncService;

    @Autowired
    private ConfluenceApiClient confluenceApiClient;

    @Autowired
    private UserRepository userRepository;

    // ============================================================================
    // CONNECTION MANAGEMENT
    // ============================================================================

    /**
     * Create a new Confluence connection.
     * POST /api/confluence/connections
     */
    @PostMapping("/connections")
    public ResponseEntity<ConnectionResponse> createConnection(
            @Valid @RequestBody CreateConnectionRequest request, Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            String organizationId = user.getOrganizationId();
            log.info("Creating Confluence connection for organization: {}", organizationId);

            ConfluenceConnection connection = syncService.createConnection(
                    organizationId,
                    request.getBaseUrl(),
                    request.getUsername(),
                    request.getApiToken(),
                    request.getConnectionName()
            );

            log.info("Successfully created connection: {}", connection.getId());
            return ResponseEntity.ok(toConnectionResponse(connection));

        } catch (IllegalArgumentException e) {
            log.error("Invalid connection parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to create Confluence connection", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get connection by ID.
     * GET /api/confluence/connections/{connectionId}
     */
    @GetMapping("/connections/{connectionId}")
    public ResponseEntity<ConnectionResponse> getConnection(@PathVariable String connectionId) {
        try {
            ConfluenceConnection connection = syncService.getConnection(connectionId);
            return ResponseEntity.ok(toConnectionResponse(connection));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all connections for the authenticated user's organization.
     * GET /api/confluence/connections
     */
    @GetMapping("/connections")
    public ResponseEntity<List<ConnectionResponse>> getConnections(Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            String organizationId = user.getOrganizationId();

            log.debug("Fetching connections for organization: {}", organizationId);
            List<ConfluenceConnection> connections = syncService.getConnectionsByOrganization(organizationId);
            List<ConnectionResponse> responses = connections.stream()
                    .map(this::toConnectionResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Failed to fetch connections", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update connection configuration.
     * PUT /api/confluence/connections/{connectionId}
     */
    @PutMapping("/connections/{connectionId}")
    public ResponseEntity<ConnectionResponse> updateConnection(
            @PathVariable String connectionId,
            @Valid @RequestBody UpdateConnectionRequest request) {
        try {
            ConfluenceConnection connection = syncService.getConnection(connectionId);

            if (request.getConnectionName() != null) {
                connection.setConnectionName(request.getConnectionName());
            }
            if (request.getUsername() != null) {
                connection.setUsername(request.getUsername());
            }
            if (request.getApiToken() != null) {
                connection.setApiToken(request.getApiToken());
            }
            if (request.getSpaceKeys() != null) {
                connection.setSpaceKeys(request.getSpaceKeys());
            }
            if (request.getEnabled() != null) {
                connection.setEnabled(request.getEnabled());
            }

            connection.setUpdatedAt(LocalDateTime.now());
            ConfluenceConnection updated = syncService.updateConnection(connection);

            return ResponseEntity.ok(toConnectionResponse(updated));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to update connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a connection.
     * DELETE /api/confluence/connections/{connectionId}
     */
    @DeleteMapping("/connections/{connectionId}")
    public ResponseEntity<Void> deleteConnection(@PathVariable String connectionId) {
        try {
            syncService.deleteConnection(connectionId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to delete connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Test connection credentials.
     * POST /api/confluence/connections/{connectionId}/test
     */
    @PostMapping("/connections/{connectionId}/test")
    public ResponseEntity<TestConnectionResponse> testConnection(@PathVariable String connectionId) {
        try {
            ConfluenceConnection connection = syncService.getConnection(connectionId);
            boolean isValid = confluenceApiClient.testConnection(
                    connection.getBaseUrl(),
                    connection.getUsername(),
                    connection.getApiToken()
            );

            TestConnectionResponse response = new TestConnectionResponse();
            response.setConnectionId(connectionId);
            response.setSuccess(isValid);
            response.setMessage(isValid ? "Connection successful" : "Connection failed");
            response.setTestedAt(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to test connection: {}", connectionId, e);
            TestConnectionResponse response = new TestConnectionResponse();
            response.setConnectionId(connectionId);
            response.setSuccess(false);
            response.setMessage("Error: " + e.getMessage());
            response.setTestedAt(LocalDateTime.now());
            return ResponseEntity.ok(response);
        }
    }

    // ============================================================================
    // SYNC OPERATIONS
    // ============================================================================

    /**
     * Start a full sync for a connection.
     * POST /api/confluence/connections/{connectionId}/sync/full
     */
    @PostMapping("/connections/{connectionId}/sync/full")
    public ResponseEntity<SyncJobResponse> startFullSync(@PathVariable String connectionId) {
        try {
            log.info("Starting full sync for connection: {}", connectionId);

            ConfluenceSyncJob job = syncService.createSyncJobForConnection(connectionId, "FULL");
            syncService.executeFullSyncAsync(job.getId());

            log.info("Full sync job created: {}", job.getId());
            return ResponseEntity.ok(toSyncJobResponse(job));

        } catch (IllegalArgumentException e) {
            log.error("Connection not found: {}", connectionId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to start full sync for connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Start an incremental sync for a connection.
     * POST /api/confluence/connections/{connectionId}/sync/incremental
     */
    @PostMapping("/connections/{connectionId}/sync/incremental")
    public ResponseEntity<SyncJobResponse> startIncrementalSync(@PathVariable String connectionId) {
        try {
            log.info("Starting incremental sync for connection: {}", connectionId);

            ConfluenceSyncJob job = syncService.createSyncJobForConnection(connectionId, "INCREMENTAL");
            syncService.executeIncrementalSyncAsync(job.getId());

            log.info("Incremental sync job created: {}", job.getId());
            return ResponseEntity.ok(toSyncJobResponse(job));

        } catch (IllegalArgumentException e) {
            log.error("Connection not found: {}", connectionId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to start incremental sync for connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Cancel a running sync job.
     * POST /api/confluence/jobs/{jobId}/cancel
     */
    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<SyncJobResponse> cancelJob(@PathVariable String jobId) {
        try {
            ConfluenceSyncJob job = syncService.getSyncJob(jobId);

            if (job.getStatus().equals("COMPLETED") || job.getStatus().equals("FAILED")) {
                return ResponseEntity.badRequest().build();
            }

            job.setStatus("CANCELLED");
            job.setCompletedAt(LocalDateTime.now());
            ConfluenceSyncJob updated = syncService.updateSyncJob(job);

            return ResponseEntity.ok(toSyncJobResponse(updated));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to cancel job: {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // SYNC JOB MONITORING
    // ============================================================================

    /**
     * Get sync job details.
     * GET /api/confluence/jobs/{jobId}
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<SyncJobResponse> getJob(@PathVariable String jobId) {
        try {
            ConfluenceSyncJob job = syncService.getSyncJob(jobId);
            return ResponseEntity.ok(toSyncJobResponse(job));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all sync jobs for a connection.
     * GET /api/confluence/connections/{connectionId}/jobs
     */
    @GetMapping("/connections/{connectionId}/jobs")
    public ResponseEntity<List<SyncJobResponse>> getJobsForConnection(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ConfluenceSyncJob> jobPage = syncService.getJobsByConnection(connectionId, pageable);

            List<SyncJobResponse> responses = jobPage.getContent().stream()
                    .map(this::toSyncJobResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Failed to fetch jobs for connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get sync job errors.
     * GET /api/confluence/jobs/{jobId}/errors
     */
    @GetMapping("/jobs/{jobId}/errors")
    public ResponseEntity<List<SyncErrorResponse>> getJobErrors(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
            Page<ConfluenceSyncError> errorPage = syncService.getErrorsByJob(jobId, pageable);

            List<SyncErrorResponse> responses = errorPage.getContent().stream()
                    .map(this::toSyncErrorResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Failed to fetch errors for job: {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // CONTENT QUERIES
    // ============================================================================

    /**
     * Get synced pages for a connection.
     * GET /api/confluence/connections/{connectionId}/pages
     */
    @GetMapping("/connections/{connectionId}/pages")
    public ResponseEntity<List<PageResponse>> getPages(
            @PathVariable String connectionId,
            @RequestParam(required = false) String spaceKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSyncedAt"));
            Page<ConfluencePage> pagePage;

            if (spaceKey != null) {
                pagePage = syncService.getPagesByConnectionAndSpace(connectionId, spaceKey, pageable);
            } else {
                pagePage = syncService.getPagesByConnection(connectionId, pageable);
            }

            List<PageResponse> responses = pagePage.getContent().stream()
                    .map(this::toPageResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Failed to fetch pages for connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get specific page details.
     * GET /api/confluence/pages/{pageId}
     */
    @GetMapping("/pages/{pageId}")
    public ResponseEntity<PageResponse> getPage(@PathVariable String pageId) {
        try {
            ConfluencePage page = syncService.getPage(pageId);
            return ResponseEntity.ok(toPageResponse(page));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get sync statistics for a connection.
     * GET /api/confluence/connections/{connectionId}/stats
     */
    @GetMapping("/connections/{connectionId}/stats")
    public ResponseEntity<SyncStatsResponse> getConnectionStats(@PathVariable String connectionId) {
        try {
            SyncStatsResponse stats = syncService.getConnectionStats(connectionId);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to fetch stats for connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // WEBHOOK HANDLING
    // ============================================================================

    /**
     * Receive Confluence webhook events.
     * POST /api/confluence/webhook/{connectionId}
     */
    @PostMapping("/webhook/{connectionId}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> payload) {
        try {
            log.info("Received webhook for connection: {}", connectionId);
            syncService.processWebhookEvent(connectionId, payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process webhook for connection: {}", connectionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // HELPER METHODS - DTO CONVERSION
    // ============================================================================

    private ConnectionResponse toConnectionResponse(ConfluenceConnection connection) {
        ConnectionResponse response = new ConnectionResponse();
        response.setId(connection.getId());
        response.setOrganizationId(connection.getOrganizationId());
        response.setConnectionName(connection.getConnectionName());
        response.setBaseUrl(connection.getBaseUrl());
        response.setUsername(connection.getUsername());
        response.setSpaceKeys(connection.getSpaceKeys());
        response.setEnabled(connection.isEnabled());
        response.setLastSyncAt(connection.getLastSyncAt());
        response.setCreatedAt(connection.getCreatedAt());
        response.setUpdatedAt(connection.getUpdatedAt());
        return response;
    }

    private SyncJobResponse toSyncJobResponse(ConfluenceSyncJob job) {
        SyncJobResponse response = new SyncJobResponse();
        response.setId(job.getId());
        response.setConnectionId(job.getConnectionId());
        response.setOrganizationId(job.getOrganizationId());
        response.setJobType(job.getJobType());
        response.setStatus(job.getStatus());
        response.setProcessedSpaces(job.getProcessedSpaces());
        response.setProcessedPages(job.getProcessedPages());
        response.setProcessedAttachments(job.getProcessedAttachments());
        response.setErrorCount(job.getErrorCount());
        response.setStartedAt(job.getStartedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setCreatedAt(job.getCreatedAt());
        return response;
    }

    private SyncErrorResponse toSyncErrorResponse(ConfluenceSyncError error) {
        SyncErrorResponse response = new SyncErrorResponse();
        response.setId(error.getId());
        response.setJobId(error.getJobId());
        response.setResourceType(error.getResourceType());
        response.setResourceId(error.getResourceId());
        response.setErrorType(error.getErrorType());
        response.setErrorMessage(error.getErrorMessage());
        response.setStackTrace(error.getStackTrace());
        response.setOccurredAt(error.getOccurredAt());
        return response;
    }

    private PageResponse toPageResponse(ConfluencePage page) {
        PageResponse response = new PageResponse();
        response.setId(page.getId());
        response.setConnectionId(page.getConnectionId());
        response.setPageId(page.getPageId());
        response.setSpaceKey(page.getSpaceKey());
        response.setTitle(page.getTitle());
        response.setContentHash(page.getContentHash());
        response.setVersion(page.getVersion());
        response.setLastModifiedBy(page.getLastModifiedBy());
        response.setLastModifiedAt(page.getLastModifiedAt());
        response.setLastSyncedAt(page.getLastSyncedAt());
        response.setCreatedAt(page.getCreatedAt());
        return response;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
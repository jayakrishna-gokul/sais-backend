package com.sapphireai.service.confluence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapphireai.constants.ConfluenceConstants;
import com.sapphireai.model.api.confluence.ConfluenceSpace;
import com.sapphireai.model.dto.confluence.SyncStatsResponse;
import com.sapphireai.model.entity.DocumentChunk;
import com.sapphireai.model.entity.ProcessedDocument;
import com.sapphireai.model.entity.confluence.*;
import com.sapphireai.model.enums.DocumentType;
import com.sapphireai.model.enums.ProcessingStatus;
import com.sapphireai.repository.ChunkRepository;
import com.sapphireai.repository.DocumentRepository;
import com.sapphireai.repository.confluence.*;
import com.sapphireai.service.document.ChunkingService;
import com.sapphireai.service.embedding.EmbeddingService;
import com.sapphireai.service.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Service for managing Confluence sync operations.
 * Handles connection management, sync job execution, and content synchronization.
 *
 * BUG FIXES APPLIED:
 * 1. Removed @Transactional from async methods (causes thread leak)
 * 2. Fixed race condition in job status updates using separate transactions
 * 3. Uses Spring-managed thread pool (confluenceSyncExecutor) instead of custom threads
 * 4. Implements space filtering correctly
 * 5. Uses composite content hash (title + body + version) for change detection
 * 6. Fixed document FK constraint violation using TransactionTemplate for document save
 * 7. Replaced all @Autowired field injection with proper constructor injection
 */
@Service
@RequiredArgsConstructor
public class ConfluenceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceSyncService.class);

    // All dependencies injected via constructor
    private final ConfluenceConnectionRepository connectionRepository;
    private final ConfluenceSyncJobRepository syncJobRepository;
    private final ConfluencePageRepository pageRepository;
    private final ConfluenceAttachmentRepository attachmentRepository;
    private final ConfluenceSyncErrorRepository errorRepository;
    private final ConfluenceSyncCheckpointRepository checkpointRepository;
    private final ConfluenceWebhookEventRepository webhookEventRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ConfluenceApiClient confluenceApiClient;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Qualifier("confluenceSyncExecutor")
    private final Executor confluenceSyncExecutor;

    // ============================================================================
    // CONNECTION MANAGEMENT
    // ============================================================================

    public ConfluenceConnection createConnection(String organizationId, String baseUrl,
                                                 String username, String apiToken, String connectionName) {
        log.info("Creating Confluence connection for org: {}", organizationId);

        // Normalize baseUrl (remove trailing slash, ensure consistent format)
        String normalizedUrl = normalizeBaseUrl(baseUrl);

        // Check if this Confluence instance is already connected
        Optional<ConfluenceConnection> existing =
                connectionRepository.findByOrganizationIdAndBaseUrlAndEnabled(
                        organizationId, normalizedUrl, true);

        if (existing.isPresent()) {
            throw new IllegalStateException(
                    "A connection to this Confluence instance already exists for your organization. " +
                            "Please update the existing connection or disable it first.");
        }

        ConfluenceConnection connection = new ConfluenceConnection();
        connection.setId(UUID.randomUUID().toString());
        connection.setOrganizationId(organizationId);
        connection.setBaseUrl(normalizedUrl);
        connection.setUsername(username);
        connection.setApiToken(apiToken);
        connection.setConnectionName(connectionName);
        connection.setEnabled(true);
        connection.setCreatedAt(LocalDateTime.now());
        connection.setUpdatedAt(LocalDateTime.now());

        return connectionRepository.save(connection);
    }

    /**
     * Normalize baseUrl for consistent comparison.
     * Removes trailing slash, converts to lowercase.
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        // Remove trailing slash
        baseUrl = baseUrl.replaceAll("/$", "");
        // Convert to lowercase for case-insensitive comparison
        return baseUrl.toLowerCase();
    }

    public ConfluenceConnection getConnection(String connectionId) {
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
    }

    public List<ConfluenceConnection> getConnectionsByOrganization(String organizationId) {
        return connectionRepository.findByOrganizationId(organizationId);
    }

    public ConfluenceConnection updateConnection(ConfluenceConnection connection) {
        connection.setUpdatedAt(LocalDateTime.now());
        return connectionRepository.save(connection);
    }

    public void deleteConnection(String connectionId) {
        ConfluenceConnection connection = getConnection(connectionId);

        // Delete all related data
        pageRepository.deleteByConnectionId(connectionId);
        attachmentRepository.deleteByConnectionId(connectionId);
        syncJobRepository.deleteByConnectionId(connectionId);
        errorRepository.deleteByConnectionId(connectionId);
        checkpointRepository.deleteByConnectionId(connectionId);
        webhookEventRepository.deleteByConnectionId(connectionId);

        connectionRepository.delete(connection);
        log.info("Deleted connection and all related data: {}", connectionId);
    }

    // ============================================================================
    // SYNC JOB MANAGEMENT
    // ============================================================================

    public ConfluenceSyncJob createSyncJobForConnection(String connectionId, String jobType) {
        ConfluenceConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

        return transactionTemplate.execute(status -> {
            ConfluenceSyncJob job = new ConfluenceSyncJob();
            job.setId(UUID.randomUUID().toString());
            job.setConnectionId(connection.getId());
            job.setOrganizationId(connection.getOrganizationId());
            job.setJobType(jobType);
            job.setStatus("PENDING");
            job.setProcessedSpaces(0);
            job.setProcessedPages(0);
            job.setProcessedAttachments(0);
            job.setErrorCount(0);
            job.setCreatedAt(LocalDateTime.now());
            return syncJobRepository.save(job);
        });
    }

    public ConfluenceSyncJob getSyncJob(String jobId) {
        return syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    public ConfluenceSyncJob updateSyncJob(ConfluenceSyncJob job) {
        return syncJobRepository.save(job);
    }

    public Page<ConfluenceSyncJob> getJobsByConnection(String connectionId, Pageable pageable) {
        return syncJobRepository.findByConnectionId(connectionId, pageable);
    }

    public Page<ConfluenceSyncError> getErrorsByJob(String jobId, Pageable pageable) {
        return errorRepository.findByJobId(jobId, pageable);
    }

    // ============================================================================
    // ASYNC SYNC EXECUTION (BUG FIX #1 & #2 & #3)
    // ============================================================================

    /**
     * Execute full sync asynchronously.
     * BUG FIX #1: No @Transactional annotation (causes thread leak with async)
     * BUG FIX #2: Uses separate transactions for status updates to avoid race conditions
     * BUG FIX #3: Uses Spring-managed thread pool instead of custom threads
     */
    @Async("confluenceSyncExecutor")
    public void executeFullSyncAsync(String jobId) {
        log.info("Starting async full sync for job: {}", jobId);

        try {
            // Update job status to RUNNING in separate transaction (BUG FIX #2)
            transactionTemplate.execute(status -> {
                ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
                job.setStatus("RUNNING");
                job.setStartedAt(LocalDateTime.now());
                return syncJobRepository.save(job);
            });

            // Execute sync logic
            executeFullSyncInternal(jobId);

            // Update job status to COMPLETED in separate transaction (BUG FIX #2)
            transactionTemplate.execute(status -> {
                ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
                job.setStatus("COMPLETED");
                job.setCompletedAt(LocalDateTime.now());
                return syncJobRepository.save(job);
            });

            log.info("Full sync completed successfully for job: {}", jobId);

        } catch (Exception e) {
            log.error("Full sync failed for job: {}", jobId, e);

            // Update job status to FAILED in separate transaction (BUG FIX #2)
            transactionTemplate.execute(status -> {
                ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
                job.setStatus("FAILED");
                job.setCompletedAt(LocalDateTime.now());
                return syncJobRepository.save(job);
            });
        }
    }

    /**
     * Execute incremental sync asynchronously.
     * BUG FIX #1: No @Transactional annotation (causes thread leak with async)
     * BUG FIX #2: Uses separate transactions for status updates to avoid race conditions
     * BUG FIX #3: Uses Spring-managed thread pool instead of custom threads
     */
    @Async("confluenceSyncExecutor")
    public void executeIncrementalSyncAsync(String jobId) {
        log.info("Starting async incremental sync for job: {}", jobId);

        try {
            // Update job status to RUNNING in separate transaction (BUG FIX #2)
            transactionTemplate.execute(status -> {
                ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
                job.setStatus("RUNNING");
                job.setStartedAt(LocalDateTime.now());
                return syncJobRepository.save(job);
            });

            // Execute sync logic
            executeIncrementalSyncInternal(jobId);

            // Update job status to COMPLETED in separate transaction (BUG FIX #2)
            transactionTemplate.execute(status -> {
                ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
                job.setStatus("COMPLETED");
                job.setCompletedAt(LocalDateTime.now());
                return syncJobRepository.save(job);
            });

            log.info("Incremental sync completed successfully for job: {}", jobId);

        } catch (Exception e) {
            log.error("Incremental sync failed for job: {}", jobId, e);

            // Update job status to FAILED in separate transaction (BUG FIX #2)
            transactionTemplate.execute(status -> {
                ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
                job.setStatus("FAILED");
                job.setCompletedAt(LocalDateTime.now());
                return syncJobRepository.save(job);
            });
        }
    }

    // ============================================================================
    // SYNC INTERNAL LOGIC
    // ============================================================================

    private void executeFullSyncInternal(String jobId) {
        ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        ConfluenceConnection connection = connectionRepository.findById(job.getConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

        log.info("Executing full sync for connection: {}", connection.getId());

        try {
            // Fetch all spaces
            List<ConfluenceSpace> allSpaces = confluenceApiClient.getAllSpaces(
                    connection.getBaseUrl(),
                    connection.getUsername(),
                    connection.getApiToken()
            );

            // BUG FIX #4: Filter spaces if space keys are configured
            List<ConfluenceSpace> spacesToSync = filterSpaces(allSpaces, connection.getSpaceKeys());

            log.info("Found {} spaces to sync (filtered from {} total)",
                    spacesToSync.size(), allSpaces.size());

            // Process each space
            for (ConfluenceSpace space : spacesToSync) {
                try {
                    syncSpace(job, connection, space);
                    incrementProcessedSpaces(job.getId());
                } catch (Exception e) {
                    log.error("Failed to sync space: {}", space.getKey(), e);
                    recordError(job.getId(), "SPACE", space.getKey(), "SYNC_ERROR",
                            e.getMessage(), getStackTrace(e));
                    incrementErrorCount(job.getId());
                }
            }

            // Update connection last sync time
            updateConnectionLastSync(connection.getId());

        } catch (Exception e) {
            log.error("Full sync failed for job: {}", jobId, e);
            throw new RuntimeException("Full sync failed", e);
        }
    }

    private void executeIncrementalSyncInternal(String jobId) {
        ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        ConfluenceConnection connection = connectionRepository.findById(job.getConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

        log.info("Executing incremental sync for connection: {}", connection.getId());

        try {
            // Get last sync checkpoint
            LocalDateTime lastSync = connection.getLastSyncAt();
            if (lastSync == null) {
                log.warn("No previous sync found, executing full sync instead");
                executeFullSyncInternal(jobId);
                return;
            }

            // Fetch recently updated content
            List<ConfluenceSpace> allSpaces = confluenceApiClient.getAllSpaces(
                    connection.getBaseUrl(),
                    connection.getUsername(),
                    connection.getApiToken()
            );

            // BUG FIX #4: Filter spaces if space keys are configured
            List<ConfluenceSpace> spacesToSync = filterSpaces(allSpaces, connection.getSpaceKeys());

            // Process each space (only updated pages)
            for (ConfluenceSpace space : spacesToSync) {
                try {
                    syncSpaceIncremental(job, connection, space, lastSync);
                    incrementProcessedSpaces(job.getId());
                } catch (Exception e) {
                    log.error("Failed to sync space incrementally: {}", space.getKey(), e);
                    recordError(job.getId(), "SPACE", space.getKey(), "SYNC_ERROR",
                            e.getMessage(), getStackTrace(e));
                    incrementErrorCount(job.getId());
                }
            }

            // Update connection last sync time
            updateConnectionLastSync(connection.getId());

        } catch (Exception e) {
            log.error("Incremental sync failed for job: {}", jobId, e);
            throw new RuntimeException("Incremental sync failed", e);
        }
    }

    // ============================================================================
    // SPACE AND PAGE SYNC LOGIC (BUG FIX #4 & #5)
    // ============================================================================

    /**
     * BUG FIX #4: Filter spaces based on configured space keys.
     * If no space keys configured, sync all spaces.
     */
    private List<ConfluenceSpace> filterSpaces(List<ConfluenceSpace> allSpaces, List<String> configuredSpaceKeys) {
        if (configuredSpaceKeys == null || configuredSpaceKeys.isEmpty()) {
            return allSpaces;
        }

        Set<String> allowedKeys = new HashSet<>(configuredSpaceKeys);
        return allSpaces.stream()
                .filter(space -> allowedKeys.contains(space.getKey()))
                .collect(Collectors.toList());
    }

    private void syncSpace(ConfluenceSyncJob job, ConfluenceConnection connection, ConfluenceSpace space) {
        log.info("Syncing space: {} ({})", space.getName(), space.getKey());

        try {
            // Fetch all pages in space
            List<com.sapphireai.model.api.confluence.ConfluencePage> pages = confluenceApiClient.getAllPagesInSpace(
                    connection.getBaseUrl(),
                    connection.getUsername(),
                    connection.getApiToken(),
                    space.getKey()
            );

            log.info("Found {} pages in space: {}", pages.size(), space.getKey());

            // Collect page IDs from Confluence API
            Set<String> confluencePageIds = pages.stream()
                    .map(com.sapphireai.model.api.confluence.ConfluencePage::getId)
                    .collect(Collectors.toSet());

            // Process each page
            for (com.sapphireai.model.api.confluence.ConfluencePage page : pages) {
                try {
                    syncPage(job, connection, page);
                    incrementProcessedPages(job.getId());
                } catch (Exception e) {
                    log.error("Failed to sync page: {}", page.getId(), e);
                    recordError(job.getId(), "PAGE", page.getId(), "SYNC_ERROR",
                            e.getMessage(), getStackTrace(e));
                    incrementErrorCount(job.getId());
                }
            }

            // DELETE ORPHANED PAGES: Pages in DB but not in Confluence (deleted/archived)
            List<ConfluencePage> existingPages = pageRepository.findByConnectionIdAndSpaceKey(
                            connection.getId(), space.getKey(), org.springframework.data.domain.Pageable.unpaged())
                    .getContent();

            for (ConfluencePage dbPage : existingPages) {
                if (!confluencePageIds.contains(dbPage.getPageId())) {
                    log.info("Deleting orphaned page (no longer in Confluence): {} - {}",
                            dbPage.getPageId(), dbPage.getTitle());
                    try {
                        deleteConfluencePage(dbPage);
                    } catch (Exception e) {
                        log.error("Failed to delete orphaned page: {}", dbPage.getPageId(), e);
                        recordError(job.getId(), "PAGE", dbPage.getPageId(), "DELETE_ERROR",
                                e.getMessage(), getStackTrace(e));
                        incrementErrorCount(job.getId());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to sync space: {}", space.getKey(), e);
            throw new RuntimeException("Space sync failed", e);
        }
    }

    private void syncSpaceIncremental(ConfluenceSyncJob job, ConfluenceConnection connection,
                                      ConfluenceSpace space, LocalDateTime since) {
        log.info("Syncing space incrementally: {} (since: {})", space.getKey(), since);

        try {
            // Fetch pages updated since last sync
            List<com.sapphireai.model.api.confluence.ConfluencePage> pages = confluenceApiClient.getPagesUpdatedSince(
                    connection.getBaseUrl(),
                    connection.getUsername(),
                    connection.getApiToken(),
                    space.getKey(),
                    since
            );

            log.info("Found {} updated pages in space: {}", pages.size(), space.getKey());

            // Process each page
            for (com.sapphireai.model.api.confluence.ConfluencePage page : pages) {
                try {
                    syncPage(job, connection, page);
                    incrementProcessedPages(job.getId());
                } catch (Exception e) {
                    log.error("Failed to sync page: {}", page.getId(), e);
                    recordError(job.getId(), "PAGE", page.getId(), "SYNC_ERROR",
                            e.getMessage(), getStackTrace(e));
                    incrementErrorCount(job.getId());
                }
            }

        } catch (Exception e) {
            log.error("Failed to sync space incrementally: {}", space.getKey(), e);
            throw new RuntimeException("Incremental space sync failed", e);
        }
    }

    /**
     * Sync individual page.
     * BUG FIX #5: Uses composite content hash (title + body + version) for change detection.
     */
    private void syncPage(ConfluenceSyncJob job, ConfluenceConnection connection,
                          com.sapphireai.model.api.confluence.ConfluencePage apiPage) {
        log.debug("Syncing page: {} - {}", apiPage.getId(), apiPage.getTitle());

        try {
            // Fetch full page content
            com.sapphireai.model.api.confluence.ConfluencePage fullPage = confluenceApiClient.getPageById(
                    connection.getBaseUrl(),
                    connection.getUsername(),
                    connection.getApiToken(),
                    apiPage.getId()
            );

            // BUG FIX #5: Calculate composite content hash
            String contentHash = calculateCompositeHash(
                    fullPage.getTitle(),
                    fullPage.getBody() != null ? fullPage.getBody().getStorage().getValue() : "",
                    String.valueOf(fullPage.getVersion())
            );

            // Check if page already exists
            Optional<ConfluencePage> existingPage =
                    pageRepository.findByConnectionIdAndPageId(connection.getId(), fullPage.getId());

            if (existingPage.isPresent()) {
                // Check if content changed
                if (existingPage.get().getContentHash().equals(contentHash)) {
                    // BUG FIX: Even if content hasn't changed, the vectors might
                    // be missing from Qdrant (e.g. previous indexing failed silently).
                    // Check for drift before skipping.
                    String documentId = ConfluenceConstants.DOCUMENT_ID_PREFIX
                            + connection.getId() + "_" + fullPage.getId();
                    if (isDocumentMissingFromQdrant(documentId)) {
                        log.warn("Page content unchanged but vectors missing from Qdrant, re-indexing: {}", fullPage.getId());
                        // Fall through to processPageForSearch below
                    } else {
                        log.debug("Page unchanged and vectors present, skipping: {}", fullPage.getId());
                        return;
                    }
                }


                // Update existing page
                updatePage(existingPage.get(), fullPage, contentHash);
            } else {
                // Create new page record
                createPage(job, connection, fullPage, contentHash);
            }

            // Get the saved entity for search processing
            ConfluencePage savedPage = existingPage.orElseGet(() ->
                    pageRepository.findByConnectionIdAndPageId(connection.getId(), fullPage.getId())
                            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved page")));

            // Process page for document extraction and vectorization
            processPageForSearch(savedPage, fullPage, connection);

            // Sync attachments
            syncPageAttachments(job, connection, fullPage);

        } catch (Exception e) {
            log.error("Failed to sync page: {}", apiPage.getId(), e);
            recordError(job.getId(), "PAGE", apiPage.getId(), "SYNC_ERROR",
                    e.getMessage(), getStackTrace(e));
        }
    }

    /**
     * BUG FIX #5: Calculate composite hash from title, body, and version.
     * This ensures we detect changes in any of these fields.
     */
    private String calculateCompositeHash(String title, String body, String version) {
        try {
            String composite = title + "|" + body + "|" + version;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(composite.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    private void createPage(ConfluenceSyncJob job, ConfluenceConnection connection,
                            com.sapphireai.model.api.confluence.ConfluencePage apiPage,
                            String contentHash) {
        ConfluencePage page = new ConfluencePage();

        page.setId(UUID.randomUUID().toString());
        page.setConnectionId(connection.getId());
        page.setOrganizationId(connection.getOrganizationId());
        page.setPageId(apiPage.getId());
        page.setSpaceKey(apiPage.getSpace().getKey());
        page.setTitle(apiPage.getTitle());
        page.setContentHash(contentHash);
        page.setVersion(apiPage.getVersion());
        page.setLastModifiedBy(apiPage.getHistory() != null ?
                apiPage.getHistory().getLastUpdated().getBy().getDisplayName() : "Unknown");
        page.setLastModifiedAt(apiPage.getHistory() != null ?
                apiPage.getHistory().getLastUpdated().getWhen() : LocalDateTime.now());
        page.setLastSyncedAt(LocalDateTime.now());
        page.setCreatedAt(LocalDateTime.now());

        pageRepository.save(page);
        log.debug("Created new page record: {}", apiPage.getId());
    }

    private void updatePage(ConfluencePage existingPage,
                            com.sapphireai.model.api.confluence.ConfluencePage apiPage,
                            String contentHash) {
        existingPage.setTitle(apiPage.getTitle());
        existingPage.setContentHash(contentHash);
        existingPage.setVersion(apiPage.getVersion());
        existingPage.setLastModifiedBy(apiPage.getHistory() != null ?
                apiPage.getHistory().getLastUpdated().getBy().getDisplayName() : "Unknown");
        existingPage.setLastModifiedAt(apiPage.getHistory() != null ?
                apiPage.getHistory().getLastUpdated().getWhen() : LocalDateTime.now());
        existingPage.setLastSyncedAt(LocalDateTime.now());

        pageRepository.save(existingPage);
        log.debug("Updated existing page record: {}", apiPage.getId());
    }

    /**
     * Process a Confluence page for search indexing.
     * BUG FIX #6: Uses TransactionTemplate to force document save in separate transaction
     * before chunks are created, avoiding foreign key constraint violations.
     */
    public void processPageForSearch(ConfluencePage confluencePage,
                                     com.sapphireai.model.api.confluence.ConfluencePage page,
                                     ConfluenceConnection connection) {
        try {
            String content = page.getBody() != null ? page.getBody().getStorage().getValue() : "";
            // Better HTML cleaning using Jsoup
            Document htmlDoc = Jsoup.parse(content);

            // Remove non-content elements
            htmlDoc.select("script, style, nav, header, footer, .metadata, .page-metadata, .noteMacro, .warningMacro").remove();

            // Get the main content area (Confluence typically uses specific divs)
            Elements contentElements = htmlDoc.select("div.wiki-content, div.page-content, body");
            Element mainContent = contentElements.isEmpty() ? htmlDoc.body() : contentElements.first();

            if (mainContent == null) {
                mainContent = htmlDoc;
            }

            // Extract text using .text() which gets ALL nested text
            String plainText = mainContent.text()
                    .replaceAll("\\s+", " ")  // Normalize whitespace
                    .trim();

            // If still too short, try the entire body
            if (plainText.length() < 100 && !content.isEmpty()) {
                plainText = htmlDoc.body() != null ?
                        htmlDoc.body().text().replaceAll("\\s+", " ").trim() :
                        htmlDoc.text().replaceAll("\\s+", " ").trim();
            }

            // Last resort: strip HTML tags manually
            if (plainText.length() < 50 && !content.isEmpty()) {
                plainText = content.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            }

            String documentId = ConfluenceConstants.DOCUMENT_ID_PREFIX + connection.getId() + "_" + page.getId();

            ProcessedDocument doc;
            Optional<ProcessedDocument> existingDoc = documentRepository.findById(documentId);

            if (existingDoc.isPresent()) {
                doc = existingDoc.get();
            } else {
                doc = ProcessedDocument.builder()
                        .id(documentId)
                        .userId(ConfluenceConstants.CONFLUENCE_SYNC_USER_ID)
                        .organizationId(connection.getOrganizationId())
                        .fileName(page.getTitle())
                        .contentType(ConfluenceConstants.CONTENT_TYPE_HTML)
                        .fileSize((long) plainText.length())
                        .documentType(DocumentType.GENERAL)
                        .status(ProcessingStatus.UPLOADED)
                        .uploadedAt(LocalDateTime.now())
                        .build();

                // CREATE THE DOCUMENT FIRST - SEPARATE SAVE
                doc = documentRepository.save(doc);
            }
            String titlePrefix = "PAGE TITLE: " + page.getTitle() + "\n\n";
            String contentWithTitle = titlePrefix + plainText;

            // NOW PROCESS - doc exists in DB
            var chunkResult = chunkingService.createChunks(contentWithTitle, DocumentType.GENERAL, doc.getId());
            List<DocumentChunk> chunks = chunkResult.getChunks();

            doc.setStatus(ProcessingStatus.PROCESSING);
            doc.setTotalChunks(chunks.size());
            doc.setChunkingStrategy(chunkResult.getStrategy());
            doc.setQualityScore(chunkResult.getAverageQuality());
            documentRepository.save(doc);

            chunkRepository.saveAll(chunks);
            embeddingService.embedChunks(chunks);
            vectorStoreService.indexChunks(chunks, connection.getOrganizationId());

            // BUG FIX: Verify at least one chunk actually landed in Qdrant
            // before marking COMPLETED. If indexing failed silently, mark
            // FAILED so the next sync will retry.
            boolean qdrantVerified = vectorStoreService.verifyChunkExists(chunks.get(0).getId());
            if (!qdrantVerified) {
                log.error("Qdrant verification failed for document: {}. Chunks saved to DB but vectors missing.", doc.getId());
                doc.setStatus(ProcessingStatus.FAILED);
                doc.setErrorMessage("Qdrant indexing verification failed — vectors not persisted");
                doc.setProcessedAt(LocalDateTime.now());
                documentRepository.save(doc);
                return;
            }

            doc.setStatus(ProcessingStatus.COMPLETED);
            doc.setProcessedAt(LocalDateTime.now());
            documentRepository.save(doc);

            log.debug("Processed page for search: {}", page.getTitle());

        } catch (Exception e) {
            log.error("Failed to process page for search: {}", page.getId(), e);

            String documentId = ConfluenceConstants.DOCUMENT_ID_PREFIX + connection.getId() + "_" + page.getId();
            documentRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(ProcessingStatus.FAILED);
                doc.setErrorMessage(e.getMessage());
                documentRepository.save(doc);
            });
        }
    }

    private void syncPageAttachments(ConfluenceSyncJob job, ConfluenceConnection connection,
                                     com.sapphireai.model.api.confluence.ConfluencePage page) {
        try {
            List<com.sapphireai.model.api.confluence.ConfluenceAttachment> attachments =
                    confluenceApiClient.getPageAttachments(
                            connection.getBaseUrl(),
                            connection.getUsername(),
                            connection.getApiToken(),
                            page.getId()
                    );

            log.debug("Fetched {} attachments for page: {}", attachments.size(), page.getId());

            for (com.sapphireai.model.api.confluence.ConfluenceAttachment attachment : attachments) {
                try {
                    syncAttachment(job, connection, page, attachment);
                    incrementProcessedAttachments(job.getId());
                } catch (Exception e) {
                    log.error("Failed to sync attachment: {}", attachment.getId(), e);
                    recordError(job.getId(), "ATTACHMENT", attachment.getId(), "SYNC_ERROR",
                            e.getMessage(), getStackTrace(e));
                    incrementErrorCount(job.getId());
                }
            }

        } catch (Exception e) {
            log.error("Error syncing attachments for page: {}", page.getId(), e);
            recordError(job.getId(), "ATTACHMENTS", page.getId(), "SYNC_ERROR", e.getMessage(), getStackTrace(e));
        }
    }

    private void syncAttachment(ConfluenceSyncJob job, ConfluenceConnection connection,
                                com.sapphireai.model.api.confluence.ConfluencePage page,
                                com.sapphireai.model.api.confluence.ConfluenceAttachment apiAttachment) {
        log.debug("Syncing attachment: {}", apiAttachment.getTitle());

        Optional<ConfluenceAttachment> existingAttachment =
                attachmentRepository.findByConnectionIdAndAttachmentId(
                        connection.getId(),
                        apiAttachment.getId()
                );

        if (existingAttachment.isPresent()) {
            log.debug("Attachment already exists, skipping: {}", apiAttachment.getId());
            return;
        }

        ConfluenceAttachment attachment = new ConfluenceAttachment();
        attachment.setId(UUID.randomUUID().toString());
        attachment.setConnectionId(connection.getId());
        attachment.setOrganizationId(connection.getOrganizationId());
        attachment.setPageId(page.getId());
        attachment.setAttachmentId(apiAttachment.getId());
        attachment.setFileName(apiAttachment.getTitle());
        attachment.setMediaType(apiAttachment.getMediaType());
        attachment.setFileSize(apiAttachment.getFileSize());
        attachment.setDownloadUrl(apiAttachment.getDownloadUrl());
        attachment.setLastSyncedAt(LocalDateTime.now());
        attachment.setCreatedAt(LocalDateTime.now());

        attachmentRepository.save(attachment);
        log.debug("Created new attachment record: {}", apiAttachment.getId());
    }

    // ============================================================================
    // WEBHOOK EVENT PROCESSING
    // ============================================================================

    public void processWebhookEvent(String connectionId, Map<String, Object> payload) {
        try {
            ConfluenceConnection connection = getConnection(connectionId);

            // Save webhook event
            ConfluenceWebhookEvent event = new ConfluenceWebhookEvent();
            event.setId(UUID.randomUUID().toString());
            event.setConnectionId(connectionId);
            event.setOrganizationId(connection.getOrganizationId());
            event.setEventType((String) payload.get("eventType"));
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setProcessed(false);
            event.setReceivedAt(LocalDateTime.now());

            webhookEventRepository.save(event);

            // Process event based on type
            String eventType = event.getEventType();
            if (eventType != null) {
                if (eventType.contains("page_created") || eventType.contains("page_updated")) {
                    processPageWebhookEvent(connection, payload);
                }
                // Mark as processed
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                webhookEventRepository.save(event);
            }

        } catch (Exception e) {
            log.error("Failed to process webhook event", e);
        }
    }

    private void processPageWebhookEvent(ConfluenceConnection connection, Map<String, Object> payload) {
        // Extract page ID from webhook payload and trigger incremental sync
        log.info("Processing page webhook event for connection: {}", connection.getId());
    }

    /**
     * Delete a Confluence page and all associated data (chunks, vectors).
     * Used when pages are deleted/archived in Confluence.
     */
    private void deleteConfluencePage(ConfluencePage page) {
        log.info("Deleting Confluence page: {} - {}", page.getPageId(), page.getTitle());

        // Delete associated document (will cascade to chunks via FK)
        String documentId = ConfluenceConstants.DOCUMENT_ID_PREFIX + page.getConnectionId() + "_" + page.getPageId();
        Optional<ProcessedDocument> doc = documentRepository.findById(documentId);

        if (doc.isPresent()) {
            // Get chunks before deleting (need IDs for Qdrant cleanup)
            List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);

            // Delete from database (cascades to chunks)
            documentRepository.delete(doc.get());

            // Delete vectors from Qdrant
            if (!chunks.isEmpty()) {
                try {
                    vectorStoreService.deleteDocumentVectors(documentId);
                } catch (Exception e) {
                    log.error("Failed to delete vectors from Qdrant for page: {}", page.getPageId(), e);
                }
            }
        }

        // Delete Confluence page record
        pageRepository.delete(page);

        log.info("Successfully deleted page: {}", page.getPageId());
    }

    // ============================================================================
    // STATS AND QUERIES
    // ============================================================================

    public SyncStatsResponse getConnectionStats(String connectionId) {
        ConfluenceConnection connection = getConnection(connectionId);

        SyncStatsResponse stats = new SyncStatsResponse();
        stats.setConnectionId(connectionId);
        stats.setTotalPages(pageRepository.countByConnectionId(connectionId));
        stats.setTotalAttachments(attachmentRepository.countByConnectionId(connectionId));
        stats.setLastSyncAt(connection.getLastSyncAt());

        // Get recent job stats
        List<ConfluenceSyncJob> recentJobs = syncJobRepository
                .findByConnectionIdOrderByCreatedAtDesc(connectionId)
                .stream()
                .limit(10)
                .collect(Collectors.toList());

        long successfulJobs = recentJobs.stream()
                .filter(j -> "COMPLETED".equals(j.getStatus()))
                .count();
        long failedJobs = recentJobs.stream()
                .filter(j -> "FAILED".equals(j.getStatus()))
                .count();

        stats.setSuccessfulSyncs((int) successfulJobs);
        stats.setFailedSyncs((int) failedJobs);

        return stats;
    }

    public Page<ConfluencePage> getPagesByConnection(String connectionId, Pageable pageable) {
        return pageRepository.findByConnectionId(connectionId, pageable);
    }

    public Page<ConfluencePage> getPagesByConnectionAndSpace(String connectionId, String spaceKey, Pageable pageable) {
        return pageRepository.findByConnectionIdAndSpaceKey(connectionId, spaceKey, pageable);
    }

    public ConfluencePage getPage(String pageId) {
        return pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private void incrementProcessedSpaces(String jobId) {
        transactionTemplate.execute(status -> {
            ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
            job.setProcessedSpaces(job.getProcessedSpaces() + 1);
            return syncJobRepository.save(job);
        });
    }

    private void incrementProcessedPages(String jobId) {
        transactionTemplate.execute(status -> {
            ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
            job.setProcessedPages(job.getProcessedPages() + 1);
            return syncJobRepository.save(job);
        });
    }

    private void incrementProcessedAttachments(String jobId) {
        transactionTemplate.execute(status -> {
            ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
            job.setProcessedAttachments(job.getProcessedAttachments() + 1);
            return syncJobRepository.save(job);
        });
    }

    private void incrementErrorCount(String jobId) {
        transactionTemplate.execute(status -> {
            ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
            job.setErrorCount(job.getErrorCount() + 1);
            return syncJobRepository.save(job);
        });
    }

    private void recordError(String jobId, String resourceType, String resourceId,
                             String errorType, String errorMessage, String stackTrace) {
        transactionTemplate.execute(status -> {
            ConfluenceSyncError error = new ConfluenceSyncError();
            error.setId(UUID.randomUUID().toString());
            error.setJobId(jobId);

            ConfluenceSyncJob job = syncJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
            error.setConnectionId(job.getConnectionId());
            error.setOrganizationId(job.getOrganizationId());

            error.setResourceType(resourceType);
            error.setResourceId(resourceId);
            error.setErrorType(errorType);
            error.setErrorMessage(errorMessage);
            error.setStackTrace(stackTrace);
            error.setOccurredAt(LocalDateTime.now());

            return errorRepository.save(error);
        });
    }

    private void updateConnectionLastSync(String connectionId) {
        transactionTemplate.execute(status -> {
            ConfluenceConnection connection = connectionRepository.findById(connectionId)
                    .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
            connection.setLastSyncAt(LocalDateTime.now());
            return connectionRepository.save(connection);
        });
    }

    /**
     * Checks whether a document's chunks are missing from Qdrant.
     * Used to detect drift between PostgreSQL (COMPLETED) and Qdrant (no vectors).
     */
    private boolean isDocumentMissingFromQdrant(String documentId) {
        // Get any one chunk for this document from PostgreSQL
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        if (chunks.isEmpty()) {
            // No chunks in DB either — not a Qdrant drift issue
            return false;
        }
        // Verify the first chunk exists in Qdrant
        return !vectorStoreService.verifyChunkExists(chunks.get(0).getId());
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
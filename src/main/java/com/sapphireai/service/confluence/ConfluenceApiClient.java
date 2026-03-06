package com.sapphireai.service.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapphireai.model.api.confluence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Client for Confluence REST API.
 * Handles authentication, rate limiting, and API interactions.
 */
@Component
public class ConfluenceApiClient {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceApiClient.class);

    private static final int MAX_RESULTS_PER_PAGE = 100;
    private static final int RATE_LIMIT_DELAY_MS = 100; // 100ms between requests
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================================
    // HELPER - API URL BUILDER
    // ============================================================================

    /**
     * Build API URL with proper prefix for Confluence Cloud vs Server.
     * Confluence Cloud (atlassian.net) requires /wiki prefix.
     * Confluence Server/Data Center does not.
     */
    private String buildApiUrl(String baseUrl, String endpoint) {
        // Remove trailing slash from baseUrl if present
        baseUrl = baseUrl.replaceAll("/$", "");

        // Confluence Cloud uses /wiki prefix
        if (baseUrl.contains("atlassian.net") && !baseUrl.endsWith("/wiki")) {
            return baseUrl + "/wiki" + endpoint;
        }

        // Confluence Server/Data Center doesn't need /wiki
        return baseUrl + endpoint;
    }

    // ============================================================================
    // CONNECTION TESTING
    // ============================================================================

    /**
     * Test connection credentials by attempting to fetch current user.
     */
    public boolean testConnection(String baseUrl, String username, String apiToken) {
        try {
            String url = buildApiUrl(baseUrl, "/rest/api/user/current");
            HttpHeaders headers = createHeaders(username, apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            return response.getStatusCode() == HttpStatus.OK;

        } catch (Exception e) {
            log.error("Connection test failed", e);
            return false;
        }
    }

    // ============================================================================
    // SPACE OPERATIONS
    // ============================================================================

    /**
     * Get all spaces from Confluence.
     */
    public List<ConfluenceSpace> getAllSpaces(String baseUrl, String username, String apiToken) {
        List<ConfluenceSpace> allSpaces = new ArrayList<>();
        int start = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = buildApiUrl(baseUrl, "/rest/api/space") +
                        String.format("?start=%d&limit=%d&expand=description.plain",
                                start, MAX_RESULTS_PER_PAGE);

                HttpHeaders headers = createHeaders(username, apiToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                applyRateLimit();

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode results = root.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode node : results) {
                        ConfluenceSpace space = parseSpace(node);
                        if (space != null) {
                            allSpaces.add(space);
                        }
                    }
                }

                // Check if there are more results
                JsonNode links = root.get("_links");
                hasMore = links != null && links.has("next");
                start += MAX_RESULTS_PER_PAGE;

            } catch (Exception e) {
                log.error("Failed to fetch spaces at start={}", start, e);
                throw new RuntimeException("Failed to fetch Confluence spaces: " + e.getMessage(), e);
            }
        }

        log.info("Fetched {} spaces from Confluence", allSpaces.size());
        return allSpaces;
    }

    /**
     * Get a specific space by key.
     */
    public ConfluenceSpace getSpace(String baseUrl, String username, String apiToken, String spaceKey) {
        try {
            String url = buildApiUrl(baseUrl, "/rest/api/space/" + spaceKey) +
                    "?expand=description.plain";

            HttpHeaders headers = createHeaders(username, apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            applyRateLimit();

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode node = objectMapper.readTree(response.getBody());
            return parseSpace(node);

        } catch (Exception e) {
            log.error("Failed to fetch space: {}", spaceKey, e);
            throw new RuntimeException("Failed to fetch space: " + spaceKey, e);
        }
    }

    // ============================================================================
    // PAGE OPERATIONS
    // ============================================================================

    /**
     * Get all pages in a space.
     */
    public List<ConfluencePage> getAllPagesInSpace(String baseUrl, String username,
                                                   String apiToken, String spaceKey) {
        List<ConfluencePage> allPages = new ArrayList<>();
        int start = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = buildApiUrl(baseUrl, "/rest/api/content") +
                        String.format("?spaceKey=%s&type=page&start=%d&limit=%d&expand=version,space,history.lastUpdated,body.storage",
                                spaceKey, start, MAX_RESULTS_PER_PAGE);

                HttpHeaders headers = createHeaders(username, apiToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                applyRateLimit();

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode results = root.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode node : results) {
                        ConfluencePage page = parsePage(node);
                        if (page != null) {
                            allPages.add(page);
                        }
                    }
                }

                // Check if there are more results
                JsonNode links = root.get("_links");
                hasMore = links != null && links.has("next");
                start += MAX_RESULTS_PER_PAGE;

            } catch (Exception e) {
                log.error("Failed to fetch pages for space {} at start={}", spaceKey, start, e);
                throw new RuntimeException("Failed to fetch pages for space " + spaceKey + ": " + e.getMessage(), e);
            }
        }

        log.info("Fetched {} pages from space: {}", allPages.size(), spaceKey);
        return allPages;
    }

    /**
     * Get pages updated since a specific date.
     */
    public List<ConfluencePage> getPagesUpdatedSince(String baseUrl, String username,
                                                     String apiToken, String spaceKey,
                                                     LocalDateTime since) {
        List<ConfluencePage> updatedPages = new ArrayList<>();

        try {
            // Confluence CQL query for pages modified after date
            String cql = String.format("space = %s AND lastModified > '%s'",
                    spaceKey, since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String url = buildApiUrl(baseUrl, "/rest/api/content/search") +
                    String.format("?cql=%s&expand=version,space,history.lastUpdated,body.storage&limit=%d",
                            cql, MAX_RESULTS_PER_PAGE);

            int start = 0;
            boolean hasMore = true;

            while (hasMore) {
                String paginatedUrl = url + "&start=" + start;
                HttpHeaders headers = createHeaders(username, apiToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                applyRateLimit();

                ResponseEntity<String> response = restTemplate.exchange(
                        paginatedUrl, HttpMethod.GET, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode results = root.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode node : results) {
                        ConfluencePage page = parsePage(node);
                        if (page != null) {
                            updatedPages.add(page);
                        }
                    }
                }

                // Check if there are more results
                JsonNode links = root.get("_links");
                hasMore = links != null && links.has("next");
                start += MAX_RESULTS_PER_PAGE;
            }

        } catch (Exception e) {
            log.error("Failed to fetch updated pages for space: {}", spaceKey, e);
        }

        log.info("Fetched {} updated pages from space {} since {}",
                updatedPages.size(), spaceKey, since);
        return updatedPages;
    }

    /**
     * Get a specific page by ID with full content.
     */
    public ConfluencePage getPageById(String baseUrl, String username,
                                      String apiToken, String pageId) {
        try {
            String url = buildApiUrl(baseUrl, "/rest/api/content/" + pageId) +
                    "?expand=version,space,history.lastUpdated,body.storage";

            HttpHeaders headers = createHeaders(username, apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            applyRateLimit();

            ResponseEntity<String> response = retryOnFailure(() ->
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class));

            JsonNode node = objectMapper.readTree(response.getBody());
            return parsePage(node);

        } catch (Exception e) {
            log.error("Failed to fetch page: {}", pageId, e);
            throw new RuntimeException("Failed to fetch page: " + pageId, e);
        }
    }

    // ============================================================================
    // ATTACHMENT OPERATIONS
    // ============================================================================

    /**
     * Get all attachments for a page.
     */
    public List<ConfluenceAttachment> getPageAttachments(String baseUrl, String username,
                                                         String apiToken, String pageId) {
        List<ConfluenceAttachment> attachments = new ArrayList<>();
        int start = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = buildApiUrl(baseUrl, "/rest/api/content/" + pageId + "/child/attachment") +
                        String.format("?start=%d&limit=%d", start, MAX_RESULTS_PER_PAGE);

                HttpHeaders headers = createHeaders(username, apiToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                applyRateLimit();

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode results = root.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode node : results) {
                        ConfluenceAttachment attachment = parseAttachment(node, baseUrl);
                        if (attachment != null) {
                            attachments.add(attachment);
                        }
                    }
                }

                // Check if there are more results
                JsonNode links = root.get("_links");
                hasMore = links != null && links.has("next");
                start += MAX_RESULTS_PER_PAGE;

            } catch (Exception e) {
                log.error("Failed to fetch attachments for page {} at start={}", pageId, start, e);
                throw new RuntimeException("Failed to fetch attachments for page " + pageId + ": " + e.getMessage(), e);
            }
        }

        log.debug("Fetched {} attachments for page: {}", attachments.size(), pageId);
        return attachments;
    }

    /**
     * Download attachment content.
     */
    public byte[] downloadAttachment(String baseUrl, String username,
                                     String apiToken, String downloadPath) {
        try {
            String url = baseUrl + downloadPath;
            HttpHeaders headers = createHeaders(username, apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            applyRateLimit();

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, byte[].class);

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to download attachment: {}", downloadPath, e);
            throw new RuntimeException("Failed to download attachment", e);
        }
    }

    // ============================================================================
    // HELPER METHODS - PARSING
    // ============================================================================

    private ConfluenceSpace parseSpace(JsonNode node) {
        try {
            ConfluenceSpace space = new ConfluenceSpace();
            space.setId(node.get("id").asText());
            space.setKey(node.get("key").asText());
            space.setName(node.get("name").asText());
            space.setType(node.get("type").asText());

            JsonNode description = node.path("description").path("plain");
            if (!description.isMissingNode()) {
                space.setDescription(description.asText());
            }

            return space;
        } catch (Exception e) {
            log.error("Failed to parse space", e);
            return null;
        }
    }

    private ConfluencePage parsePage(JsonNode node) {
        try {
            ConfluencePage page = new ConfluencePage();
            page.setId(node.get("id").asText());
            page.setType(node.get("type").asText());
            page.setTitle(node.get("title").asText());

            // Parse space
            JsonNode spaceNode = node.get("space");
            if (spaceNode != null) {
                ConfluenceSpace space = new ConfluenceSpace();
                space.setId(spaceNode.get("id").asText());
                space.setKey(spaceNode.get("key").asText());
                space.setName(spaceNode.get("name").asText());
                page.setSpace(space);
            }

            // Parse version
            JsonNode versionNode = node.get("version");
            if (versionNode != null) {
                page.setVersion(versionNode.get("number").asInt());
            }

            // Parse body
            JsonNode bodyNode = node.path("body").path("storage");
            if (!bodyNode.isMissingNode()) {
                ConfluencePage.Body body = new ConfluencePage.Body();
                ConfluencePage.Storage storage = new ConfluencePage.Storage();
                storage.setValue(bodyNode.get("value").asText());
                storage.setRepresentation(bodyNode.get("representation").asText());
                body.setStorage(storage);
                page.setBody(body);
            }

            // Parse history
            JsonNode historyNode = node.path("history").path("lastUpdated");
            if (!historyNode.isMissingNode()) {
                ConfluencePage.History history = new ConfluencePage.History();
                ConfluencePage.LastUpdated lastUpdated = new ConfluencePage.LastUpdated();

                JsonNode byNode = historyNode.get("by");
                if (byNode != null) {
                    ConfluencePage.User user = new ConfluencePage.User();
                    user.setDisplayName(byNode.get("displayName").asText());
                    user.setEmail(byNode.path("email").asText(null));
                    lastUpdated.setBy(user);
                }

                String whenStr = historyNode.get("when").asText();
                lastUpdated.setWhen(LocalDateTime.parse(whenStr,
                        DateTimeFormatter.ISO_DATE_TIME));

                history.setLastUpdated(lastUpdated);
                page.setHistory(history);
            }

            return page;
        } catch (Exception e) {
            log.error("Failed to parse page", e);
            return null;
        }
    }

    private ConfluenceAttachment parseAttachment(JsonNode node, String baseUrl) {
        try {
            ConfluenceAttachment attachment = new ConfluenceAttachment();
            attachment.setId(node.get("id").asText());
            attachment.setType(node.get("type").asText());
            attachment.setTitle(node.get("title").asText());

            JsonNode metadata = node.get("metadata");
            if (metadata != null) {
                attachment.setMediaType(metadata.path("mediaType").asText());
                attachment.setFileSize(metadata.path("fileSize").asLong());
            }

            JsonNode links = node.get("_links");
            if (links != null) {
                String downloadPath = links.get("download").asText();
                attachment.setDownloadUrl(downloadPath);
            }

            return attachment;
        } catch (Exception e) {
            log.error("Failed to parse attachment", e);
            return null;
        }
    }

    // ============================================================================
    // HELPER METHODS - HTTP & RATE LIMITING
    // ============================================================================

    private HttpHeaders createHeaders(String username, String apiToken) {
        HttpHeaders headers = new HttpHeaders();

        if (username != null && !username.isEmpty() &&
                apiToken != null && !apiToken.isEmpty()) {
            String auth = username + ":" + apiToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        return headers;
    }

    private void applyRateLimit() {
        try {
            TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rate limit sleep interrupted", e);
        }
    }

    private <T> T retryOnFailure(ThrowingSupplier<T> supplier) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return supplier.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Rate limited, retrying... (attempt {}/{})", attempt, MAX_RETRIES);
                lastException = e;
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    throw e;
                }
                log.warn("Request failed, retrying... (attempt {}/{})", attempt, MAX_RETRIES);
                lastException = e;
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
            }
        }

        throw lastException != null ? lastException :
                new RuntimeException("All retry attempts failed");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
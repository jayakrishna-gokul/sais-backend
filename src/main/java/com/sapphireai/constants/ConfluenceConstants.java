package com.sapphireai.constants;

/**
 * System-wide constants for Confluence connector.
 * Centralizes all magic strings and configuration values.
 */
public final class ConfluenceConstants {

    private ConfluenceConstants() {
        // Prevent instantiation
    }

    /**
     * System user ID for Confluence sync operations.
     * This user is automatically created on application startup.
     */
    public static final String CONFLUENCE_SYNC_USER_ID = "confluence_sync";

    /**
     * System organization ID for system-level operations.
     */
    public static final String SYSTEM_ORG_ID = "SYSTEM";

    /**
     * Prefix for Confluence document IDs in the search index.
     */
    public static final String DOCUMENT_ID_PREFIX = "confluence_";

    /**
     * Content type for Confluence pages.
     */
    public static final String CONTENT_TYPE_HTML = "text/html";

    /**
     * Default batch size for page sync operations.
     */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /**
     * Maximum retries for failed API calls.
     */
    public static final int MAX_API_RETRIES = 3;

    /**
     * Timeout for API calls in seconds.
     */
    public static final int API_TIMEOUT_SECONDS = 30;
}
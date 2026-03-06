-- V4__Add_Confluence_Connector_Tables.sql
-- Confluence connector schema matching entity classes EXACTLY

-- Create SYSTEM organization if it doesn't exist
INSERT INTO organizations (id, name, created_at)
VALUES ('SYSTEM', 'System Organization', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Create SAIS organization for system users
INSERT INTO organizations (id, name, created_at)
VALUES ('SAIS', 'SAIS Organization', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Create confluence_sync system user
INSERT INTO users (id, email, password, full_name, role, organization_id, enabled, created_at, updated_at)
VALUES (
    'confluence_sync',
    'confluence_sync@system.local',
    'N/A',
    'Confluence Sync System',
    'SYSTEM',
    'SYSTEM',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- CONFLUENCE CONNECTION
-- ============================================
CREATE TABLE confluence_connection (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(50) NOT NULL,
    connection_name VARCHAR(255),
    instance_url VARCHAR(500) NOT NULL,
    username VARCHAR(255),
    api_token TEXT,
    sync_enabled BOOLEAN DEFAULT true,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_confluence_connection_org ON confluence_connection(organization_id);

-- ============================================
-- CONNECTION SPACES (Join table for @ElementCollection)
-- ============================================
CREATE TABLE confluence_connection_space (
    connection_id VARCHAR(36) NOT NULL,
    space_key VARCHAR(50) NOT NULL,
    PRIMARY KEY (connection_id, space_key),
    CONSTRAINT fk_space_connection FOREIGN KEY (connection_id)
        REFERENCES confluence_connection(id) ON DELETE CASCADE
);

CREATE INDEX idx_connection_space_connection ON confluence_connection_space(connection_id);

-- ============================================
-- SYNC JOBS
-- ============================================
CREATE TABLE confluence_sync_job (
    id VARCHAR(36) PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    processed_spaces INTEGER DEFAULT 0,
    processed_pages INTEGER DEFAULT 0,
    processed_attachments INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_sync_job_connection FOREIGN KEY (connection_id)
        REFERENCES confluence_connection(id) ON DELETE CASCADE
);

CREATE INDEX idx_sync_job_connection ON confluence_sync_job(connection_id);

-- ============================================
-- CONFLUENCE PAGES
-- ============================================
CREATE TABLE confluence_page (
    id VARCHAR(36) PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    page_id VARCHAR(100) NOT NULL,
    space_key VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_at TIMESTAMP,
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_page_connection FOREIGN KEY (connection_id)
        REFERENCES confluence_connection(id) ON DELETE CASCADE
);

CREATE INDEX idx_confluence_page_connection ON confluence_page(connection_id);
CREATE INDEX idx_confluence_page_space ON confluence_page(connection_id, space_key);
CREATE INDEX idx_confluence_page_page_id ON confluence_page(connection_id, page_id);

-- ============================================
-- CONFLUENCE ATTACHMENTS
-- ============================================
CREATE TABLE confluence_attachment (
    id VARCHAR(36) PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    confluence_page_id VARCHAR(100) NOT NULL,
    confluence_attachment_id VARCHAR(100) NOT NULL,
    confluence_file_name VARCHAR(500) NOT NULL,
    media_type VARCHAR(100),
    file_size BIGINT,
    download_url VARCHAR(1000),
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_attachment_connection FOREIGN KEY (connection_id)
        REFERENCES confluence_connection(id) ON DELETE CASCADE
);

CREATE INDEX idx_confluence_attachment_connection ON confluence_attachment(connection_id);

-- ============================================
-- SYNC ERRORS
-- ============================================
CREATE TABLE confluence_sync_error (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    connection_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT,
    stack_trace TEXT,
    occurred_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_error_job FOREIGN KEY (job_id)
        REFERENCES confluence_sync_job(id) ON DELETE CASCADE
);

CREATE INDEX idx_confluence_sync_error_job ON confluence_sync_error(job_id);
CREATE INDEX idx_confluence_sync_error_connection ON confluence_sync_error(connection_id);

-- ============================================
-- SYNC CHECKPOINTS
-- ============================================
CREATE TABLE confluence_sync_checkpoint (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    connection_id VARCHAR(36) NOT NULL,
    space_key VARCHAR(100),
    last_synced_page_id VARCHAR(100),
    checkpoint_data TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_checkpoint_job FOREIGN KEY (job_id)
        REFERENCES confluence_sync_job(id) ON DELETE CASCADE
);

CREATE INDEX idx_confluence_checkpoint_job ON confluence_sync_checkpoint(job_id);
CREATE INDEX idx_confluence_checkpoint_connection ON confluence_sync_checkpoint(connection_id);

-- ============================================
-- WEBHOOK EVENTS
-- ============================================
CREATE TABLE confluence_webhook_event (
    id VARCHAR(36) PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT,
    processed BOOLEAN NOT NULL DEFAULT false,
    processed_at TIMESTAMP,
    received_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_webhook_connection FOREIGN KEY (connection_id)
        REFERENCES confluence_connection(id) ON DELETE CASCADE
);

CREATE INDEX idx_confluence_webhook_connection ON confluence_webhook_event(connection_id);
CREATE INDEX idx_confluence_webhook_processed ON confluence_webhook_event(processed);
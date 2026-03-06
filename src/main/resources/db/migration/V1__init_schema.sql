-- Sapphire AI Service - Initial Database Schema
-- PostgreSQL 16+ required

-- ============================================
-- ORGANIZATIONS TABLE (Multi-tenancy)
-- ============================================
CREATE TABLE organizations (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) UNIQUE,
    sso_provider VARCHAR(50),
    sso_config TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_org_domain ON organizations(domain);
CREATE INDEX idx_org_enabled ON organizations(enabled);

-- ============================================
-- USERS TABLE
-- ============================================
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    organization VARCHAR(255),
    organization_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_org ON users(organization_id);
CREATE INDEX idx_user_enabled ON users(enabled);

-- ============================================
-- DOCUMENTS TABLE
-- ============================================
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    filename VARCHAR(500),
    file_path VARCHAR(1000),
    content_type VARCHAR(100),
    file_size BIGINT,
    document_type VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    total_chunks INTEGER,
    processing_time_ms BIGINT,
    chunking_strategy VARCHAR(100),
    quality_score DOUBLE PRECISION,
    detection_confidence DOUBLE PRECISION,
    requires_review BOOLEAN DEFAULT FALSE,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message VARCHAR(1000),
    CONSTRAINT fk_doc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE INDEX idx_doc_user ON documents(user_id);
CREATE INDEX idx_doc_org ON documents(organization_id);
CREATE INDEX idx_doc_status ON documents(status);
CREATE INDEX idx_doc_uploaded ON documents(uploaded_at DESC);
CREATE INDEX idx_doc_review ON documents(requires_review) WHERE requires_review = TRUE;

-- ============================================
-- CHUNKS TABLE
-- ============================================
CREATE TABLE chunks (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    chunk_index INTEGER,
    content TEXT NOT NULL,
    chunk_type VARCHAR(50),
    token_count INTEGER,
    quality_score DOUBLE PRECISION,
    parent_chunk_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_parent FOREIGN KEY (parent_chunk_id) REFERENCES chunks(id) ON DELETE SET NULL
);

CREATE INDEX idx_chunk_doc ON chunks(document_id);
CREATE INDEX idx_chunk_parent ON chunks(parent_chunk_id);
CREATE INDEX idx_chunk_index ON chunks(document_id, chunk_index);
CREATE INDEX idx_chunk_quality ON chunks(quality_score);

-- Full-text search on chunk content (PostgreSQL-specific)
CREATE INDEX idx_chunk_content_fts ON chunks USING GIN(to_tsvector('english', content));

-- ============================================
-- CHUNK METADATA TABLE (key-value pairs)
-- ============================================
CREATE TABLE chunk_metadata (
    chunk_id VARCHAR(36) NOT NULL,
    meta_key VARCHAR(100) NOT NULL,
    meta_value TEXT,
    PRIMARY KEY (chunk_id, meta_key),
    CONSTRAINT fk_metadata_chunk FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE
);

CREATE INDEX idx_metadata_key ON chunk_metadata(meta_key);

-- ============================================
-- CONVERSATION SESSIONS TABLE
-- ============================================
CREATE TABLE conversation_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    title VARCHAR(500),
    turn_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_user ON conversation_sessions(user_id);
CREATE INDEX idx_session_org ON conversation_sessions(organization_id);
CREATE INDEX idx_session_activity ON conversation_sessions(last_activity_at DESC);

-- ============================================
-- CONVERSATION TURNS TABLE
-- ============================================
CREATE TABLE conversation_turns (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    query VARCHAR(2000) NOT NULL,
    response TEXT,
    confidence DOUBLE PRECISION,
    sources VARCHAR(5000),
    processing_time_ms BIGINT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_turn_session FOREIGN KEY (session_id) REFERENCES conversation_sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_turn_session ON conversation_turns(session_id, timestamp);
CREATE INDEX idx_turn_timestamp ON conversation_turns(timestamp DESC);
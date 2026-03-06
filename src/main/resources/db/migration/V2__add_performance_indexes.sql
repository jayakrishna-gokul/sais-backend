-- Performance optimization indexes
-- Based on common query patterns

-- ============================================
-- COMPOSITE INDEXES FOR COMMON QUERIES
-- ============================================

-- Document search by org + status + date
CREATE INDEX idx_doc_org_status_date ON documents(organization_id, status, uploaded_at DESC);

-- User lookup by org + email
CREATE INDEX idx_user_org_email ON users(organization_id, email);

-- Session lookup by org + user + activity
CREATE INDEX idx_session_org_user_activity ON conversation_sessions(
    organization_id, 
    user_id, 
    last_activity_at DESC
);

-- ============================================
-- PARTIAL INDEXES (PostgreSQL-specific)
-- ============================================

-- Only index active/processing documents
CREATE INDEX idx_doc_active ON documents(organization_id, status) 
WHERE status IN ('UPLOADED', 'PROCESSING');

-- Only index recent sessions (last 30 days)
CREATE INDEX idx_session_recent ON conversation_sessions(user_id, last_activity_at DESC);
--WHERE last_activity_at > (CURRENT_TIMESTAMP - INTERVAL '30 days');

-- ============================================
-- STATISTICS FOR QUERY PLANNER
-- ============================================

-- Update statistics for better query planning
ANALYZE organizations;
ANALYZE users;
ANALYZE documents;
ANALYZE chunks;
ANALYZE conversation_sessions;
ANALYZE conversation_turns;
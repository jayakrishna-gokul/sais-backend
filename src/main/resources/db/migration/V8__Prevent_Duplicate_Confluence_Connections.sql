-- V8__Prevent_Duplicate_Confluence_Connections.sql
-- Prevents duplicate Confluence connections for the same instance (org + instance_url)
-- Allows multiple connections per org for different Confluence instances

-- Create unique index: one connection per organization per Confluence instance
-- Only applies to sync_enabled connections (allows soft-delete pattern)
CREATE UNIQUE INDEX idx_confluence_connection_unique
ON confluence_connection (organization_id, instance_url)
WHERE sync_enabled = true;

-- Add comment for documentation
COMMENT ON INDEX idx_confluence_connection_unique IS
'Ensures one active connection per organization per Confluence instance (instance_url). Allows multiple Confluence instances per org.';
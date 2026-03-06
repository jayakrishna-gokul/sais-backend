-- ================================================
-- V7: Fix Confluence Sync User Organization
-- Changes confluence_sync user from SYSTEM to SAIS
-- ================================================

-- Update confluence_sync user to use SAIS organization
UPDATE users
SET organization_id = 'SAIS'
WHERE id = 'confluence_sync';
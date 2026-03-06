-- Fix document ID length to accommodate Confluence IDs
ALTER TABLE chunks ALTER COLUMN document_id TYPE VARCHAR(100);
ALTER TABLE documents ALTER COLUMN id TYPE VARCHAR(100);
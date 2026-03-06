-- Collection metadata table
-- Tracks which embedding model was used to create each collection
-- Prevents dimension mismatches when switching embedding models

CREATE TABLE collection_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_name VARCHAR(255) NOT NULL UNIQUE,
    embedding_provider VARCHAR(50) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    dimension INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_collection_metadata_name ON collection_metadata(collection_name);

-- Trigger to update updated_at automatically
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_collection_metadata_updated_at
    BEFORE UPDATE ON collection_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
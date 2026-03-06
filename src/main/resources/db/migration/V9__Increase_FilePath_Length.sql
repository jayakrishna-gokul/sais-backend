-- V9__Increase_FilePath_Length.sql
-- Increase file_path column to accommodate long filenames with UUID prefix

ALTER TABLE documents
ALTER COLUMN file_path TYPE VARCHAR(1000);
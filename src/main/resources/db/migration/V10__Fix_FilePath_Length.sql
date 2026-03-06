-- V10__Fix_FilePath_Length.sql
-- Fix file_path column length (V9 was deployed empty by mistake)

ALTER TABLE documents
ALTER COLUMN file_path TYPE VARCHAR(1000);
package com.sapphireai.model.enums;

/**
 * Document processing workflow status.
 */
public enum ProcessingStatus {
    UPLOADED,    // File uploaded, queued for processing
    PROCESSING,  // Currently being processed (chunking, embedding, indexing)
    COMPLETED,   // Successfully processed and indexed
    FAILED,      // Processing failed (see errorMessage)
    DELETED      // Marked for deletion
}

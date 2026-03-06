package com.sapphireai.model.entity;

import com.sapphireai.model.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents an uploaded and processed document.
 * Tracks processing status, chunking results, and quality metrics.
 */
@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_doc_user", columnList = "user_id"),
        @Index(name = "idx_doc_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedDocument {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "filename", length = 500)
    private String fileName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Organization ID - which company owns this document.
     * Enables org-wide search (not just user's uploads).
     */
    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    /**
     * Detected document type (POLICY, TECHNICAL, GENERAL, etc.)
     * Used to select appropriate chunking strategy.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocumentType documentType;

    /**
     * Current processing status (UPLOADED, PROCESSING, COMPLETED, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProcessingStatus status;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * Which chunking algorithm was used
     * Examples: "HybridChunking", "SemanticChunking"
     */
    @Column(name = "chunking_strategy")
    private String chunkingStrategy;

    /**
     * Quality score of chunking (0.0 to 1.0)
     * Measures: sentence completeness, chunk size appropriateness, boundary quality
     */
    @Column(name = "quality_score")
    private Double qualityScore;

    /**
     * Confidence in document type detection (0.0 to 1.0)
     * Low confidence triggers fallback to general chunking
     */
    @Column(name = "detection_confidence")
    private Double detectionConfidence;

    /**
     * Flag indicating document needs manual review
     * Set when: low quality, low confidence, or processing errors
     */
    @Column(name = "requires_review")
    private Boolean requiresReview = false;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
package com.sapphireai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a chunk (piece) of a processed document.
 * Used for semantic search and RAG context building.
 *
 * PARENT-CHILD ARCHITECTURE (for 91% accuracy):
 * - Child chunks: Small, precise (500-800 chars) - used for search
 * - Parent chunks: Larger context (2000-3000 chars) - sent to LLM
 * This ensures search precision while maintaining context quality.
 */
@Entity
@Table(name = "chunks", indexes = {
        @Index(name = "idx_chunk_doc", columnList = "document_id"),
        @Index(name = "idx_chunk_parent", columnList = "parent_chunk_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    /**
     * Position of this chunk in document (0-indexed)
     */
    @Column(name = "chunk_index")
    private Integer chunkIndex;

    /**
     * Actual text content of this chunk.
     * Max 10,000 characters to fit within LLM context windows.
     */
    @Column(name = "content", length = 10000, nullable = false)
    private String content;

    /**
     * Type of chunk: POLICY_CLAUSE, PARAGRAPH, CODE_BLOCK, etc.
     * Used for result formatting and display.
     */
    @Column(name = "chunk_type")
    private String chunkType;

    /**
     * Approximate token count (for LLM context management)
     * Formula: characters / 4
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    /**
     * Quality score (0.0 to 1.0)
     * Measures: sentence completeness, size, boundary quality
     */
    @Column(name = "quality_score")
    private Double qualityScore;

    /**
     * CRITICAL FOR 91% ACCURACY:
     * Reference to parent chunk containing larger context.
     *
     * Flow:
     * 1. Search finds child chunk (this chunk) - precise match
     * 2. Retrieve parent chunk - larger context
     * 3. Send parent to LLM - better answer quality
     */
    @Column(name = "parent_chunk_id")
    private String parentChunkId;

    /**
     * Metadata for enhanced search and display.
     * Examples: {"section": "3.2", "page": "15", "heading": "Vacation Policy"}
     */
    @ElementCollection
    @CollectionTable(name = "chunk_metadata", joinColumns = @JoinColumn(name = "chunk_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== SEARCH RESULT FIELDS (not persisted) ==========

    /**
     * Final relevance score (calculated during search)
     * Formula: (vectorScore * 0.5) + (bm25Score * 0.3) + (metadataScore * 0.2)
     */
    @Transient
    private Double relevanceScore;

    /**
     * Vector similarity score from Qdrant (cosine similarity: 0.0 to 1.0)
     */
    @Transient
    private Double vectorScore;

    @Transient // Don't store in DB, too large
    private transient float[] embedding;

    /**
     * BM25 keyword matching score (0.0 to 1.0)
     * Used in hybrid search for exact keyword matches
     */
    @Transient
    private Double bm25Score;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

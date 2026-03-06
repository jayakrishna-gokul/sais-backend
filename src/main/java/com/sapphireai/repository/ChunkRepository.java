package com.sapphireai.repository;

import com.sapphireai.model.entity.DocumentChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Chunk repository for document fragments.
 * NOTE: Vector search happens in Qdrant, this is for metadata only.
 */
@Repository
public interface ChunkRepository extends JpaRepository<DocumentChunk, String> {

    /**
     * Find all chunks for a document (ordered by position).
     * Used for: Document viewer, chunk debugging
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);

    /**
     * Find parent chunks (used for context expansion).
     * When child chunk matches query, we retrieve parent for more context.
     */
    List<DocumentChunk> findByParentChunkId(String parentChunkId);

    /**
     * Find chunks by IDs (used after vector search).
     * Vector search in Qdrant returns chunk IDs → fetch full chunks from Postgres
     */
    List<DocumentChunk> findByIdIn(List<String> chunkIds);

    /**
     * Count chunks for document.
     * Used for: Quality metrics, progress tracking
     */
    long countByDocumentId(String documentId);

    /**
     * Delete all chunks for document.
     * Used for: Document deletion cascade, reprocessing
     */
    void deleteByDocumentId(String documentId);

    /**
     * Find chunks with low quality score.
     * Used for: Quality control, identifying bad chunking
     */
    @Query("SELECT c FROM DocumentChunk c WHERE c.qualityScore < :threshold")
    Page<DocumentChunk> findLowQualityChunks(@Param("threshold") Double threshold, Pageable pageable);

    /**
     * Get average quality score for document.
     * Used for: Document quality metrics
     */
    @Query("SELECT AVG(c.qualityScore) FROM DocumentChunk c WHERE c.documentId = :docId")
    Double getAverageQualityScore(@Param("docId") String documentId);

    /**
     * Count all chunks belonging to an organization.
     *
     * WHY @Query instead of derived method name:
     * DocumentChunk stores documentId as a plain String — there is no @ManyToOne
     * association to ProcessedDocument. Spring Data cannot traverse
     * "document.organizationId" because no such navigable relationship exists.
     * The explicit JOIN on the documentId field is required.
     */
    @Query("SELECT COUNT(c) FROM DocumentChunk c " +
            "JOIN ProcessedDocument d ON c.documentId = d.id " +
            "WHERE d.organizationId = :orgId")
    long countByOrganizationId(@Param("orgId") String organizationId);
}
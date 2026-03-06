package com.sapphireai.repository;

import com.sapphireai.model.entity.ProcessedDocument;
import com.sapphireai.model.enums.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Document repository with organization-scoped queries.
 * CRITICAL: All queries filter by organizationId for multi-tenancy.
 */
@Repository
public interface DocumentRepository extends JpaRepository<ProcessedDocument, String> {

    /**
     * Find all documents in organization.
     * Used for: "My Documents" page showing ALL company docs
     */
    Page<ProcessedDocument> findByOrganizationId(String organizationId, Pageable pageable);

    /**
     * Find documents uploaded by specific user in their organization.
     * Used for: "My Uploads" filter
     */
    Page<ProcessedDocument> findByOrganizationIdAndUserId(
            String organizationId,
            String userId,
            Pageable pageable
    );

    /**
     * Find documents by status in organization.
     * Used for: Admin dashboard showing processing queue
     */
    Page<ProcessedDocument> findByOrganizationIdAndStatus(
            String organizationId,
            ProcessingStatus status,
            Pageable pageable
    );

    /**
     * Find documents requiring manual review in organization.
     * Used for: Quality control dashboard
     */
    Page<ProcessedDocument> findByOrganizationIdAndRequiresReviewTrue(
            String organizationId,
            Pageable pageable
    );

    /**
     * Search documents by filename in organization.
     * Used for: Document search/filter
     */
    @Query("SELECT d FROM ProcessedDocument d " +
            "WHERE d.organizationId = :orgId " +
            "AND LOWER(d.fileName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<ProcessedDocument> searchByFilename(
            @Param("orgId") String organizationId,
            @Param("search") String searchTerm,
            Pageable pageable
    );

    /**
     * Find recent documents in organization.
     * Used for: "Recently Added" section
     */
    List<ProcessedDocument> findTop10ByOrganizationIdOrderByUploadedAtDesc(String organizationId);

    /**
     * Security check: Verify user can delete document.
     * User can only delete documents THEY uploaded.
     */
    Optional<ProcessedDocument> findByIdAndOrganizationIdAndUserId(
            String id,
            String organizationId,
            String userId
    );

    /**
     * Count documents by status in organization.
     * Used for: Dashboard statistics
     */
    long countByOrganizationIdAndStatus(String organizationId, ProcessingStatus status);

    /**
     * Find failed documents older than given date.
     * Used for: Cleanup job (retry or delete old failures)
     */
    List<ProcessedDocument> findByOrganizationIdAndStatusAndProcessedAtBefore(
            String organizationId,
            ProcessingStatus status,
            LocalDateTime before
    );

    long countByOrganizationId(String organizationId);
}
package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluenceAttachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfluenceAttachment entity.
 */
@Repository
public interface ConfluenceAttachmentRepository extends JpaRepository<ConfluenceAttachment, String> {

    /**
     * Find all attachments for a connection.
     */
    Page<ConfluenceAttachment> findByConnectionId(String connectionId, Pageable pageable);

    /**
     * Find all attachments for a page.
     */
    List<ConfluenceAttachment> findByPageId(String pageId);

    /**
     * Find attachment by connection and Confluence attachment ID.
     */
    Optional<ConfluenceAttachment> findByConnectionIdAndAttachmentId(String connectionId, String attachmentId);

    /**
     * Find attachments by organization.
     */
    Page<ConfluenceAttachment> findByOrganizationId(String organizationId, Pageable pageable);

    /**
     * Find attachments synced before a specific date (for cleanup).
     */
    List<ConfluenceAttachment> findByConnectionIdAndLastSyncedAtBefore(String connectionId, LocalDateTime date);

    /**
     * Find attachments by media type.
     */
    List<ConfluenceAttachment> findByConnectionIdAndMediaType(String connectionId, String mediaType);

    /**
     * Count attachments for a connection.
     */
    long countByConnectionId(String connectionId);

    /**
     * Count attachments for a page.
     */
    long countByPageId(String pageId);

    /**
     * Check if attachment exists.
     */
    boolean existsByConnectionIdAndAttachmentId(String connectionId, String attachmentId);

    /**
     * Delete all attachments for a connection.
     */
    void deleteByConnectionId(String connectionId);

    /**
     * Delete all attachments for an organization.
     */
    void deleteByOrganizationId(String organizationId);

    /**
     * Delete all attachments for a page.
     */
    void deleteByPageId(String pageId);
}
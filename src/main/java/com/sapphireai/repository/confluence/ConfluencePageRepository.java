package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluencePage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfluencePage entity.
 */
@Repository
public interface ConfluencePageRepository extends JpaRepository<ConfluencePage, String> {

    /**
     * Find all pages for a connection.
     */
    Page<ConfluencePage> findByConnectionId(String connectionId, Pageable pageable);

    /**
     * Find pages by connection and space.
     */
    Page<ConfluencePage> findByConnectionIdAndSpaceKey(String connectionId, String spaceKey, Pageable pageable);

    /**
     * Find page by connection and Confluence page ID.
     */
    Optional<ConfluencePage> findByConnectionIdAndPageId(String connectionId, String pageId);

    /**
     * Find pages by organization.
     */
    Page<ConfluencePage> findByOrganizationId(String organizationId, Pageable pageable);

    /**
     * Find pages modified after a specific date.
     */
    List<ConfluencePage> findByConnectionIdAndLastModifiedAtAfter(String connectionId, LocalDateTime date);

    /**
     * Find pages synced before a specific date (for cleanup).
     */
    List<ConfluencePage> findByConnectionIdAndLastSyncedAtBefore(String connectionId, LocalDateTime date);

    /**
     * Search pages by title.
     */
    @Query("SELECT p FROM ConfluencePage p WHERE p.connectionId = :connectionId AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ConfluencePage> searchByTitle(@Param("connectionId") String connectionId,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);

    /**
     * Count pages for a connection.
     */
    long countByConnectionId(String connectionId);

    /**
     * Count pages for a space.
     */
    long countByConnectionIdAndSpaceKey(String connectionId, String spaceKey);

    /**
     * Check if page exists.
     */
    boolean existsByConnectionIdAndPageId(String connectionId, String pageId);

    /**
     * Delete all pages for a connection.
     */
    void deleteByConnectionId(String connectionId);

    /**
     * Delete all pages for an organization.
     */
    void deleteByOrganizationId(String organizationId);

    /**
     * Delete pages for a specific space.
     */
    void deleteByConnectionIdAndSpaceKey(String connectionId, String spaceKey);

    @Query("""
        SELECT MAX(cp.lastSyncedAt) FROM ConfluencePage cp
        WHERE cp.connectionId = :connectionId
        """)
    Optional<LocalDateTime> findLatestSyncTimestamp(@Param("connectionId") String connectionId);

}
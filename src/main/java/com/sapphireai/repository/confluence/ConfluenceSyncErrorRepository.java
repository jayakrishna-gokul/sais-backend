package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluenceSyncError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ConfluenceSyncError entity.
 */
@Repository
public interface ConfluenceSyncErrorRepository extends JpaRepository<ConfluenceSyncError, String> {

    /**
     * Find all errors for a job.
     */
    Page<ConfluenceSyncError> findByJobId(String jobId, Pageable pageable);

    /**
     * Find all errors for a connection.
     */
    Page<ConfluenceSyncError> findByConnectionId(String connectionId, Pageable pageable);

    /**
     * Find errors by resource type.
     */
    List<ConfluenceSyncError> findByJobIdAndResourceType(String jobId, String resourceType);

    /**
     * Find errors by error type.
     */
    List<ConfluenceSyncError> findByJobIdAndErrorType(String jobId, String errorType);

    /**
     * Find errors for a specific resource.
     */
    List<ConfluenceSyncError> findByJobIdAndResourceTypeAndResourceId(String jobId, String resourceType, String resourceId);

    /**
     * Find errors occurred after a specific date.
     */
    List<ConfluenceSyncError> findByConnectionIdAndOccurredAtAfter(String connectionId, LocalDateTime date);

    /**
     * Find recent errors for a connection.
     */
    @Query("SELECT e FROM ConfluenceSyncError e WHERE e.connectionId = :connectionId ORDER BY e.occurredAt DESC")
    Page<ConfluenceSyncError> findRecentErrors(@Param("connectionId") String connectionId, Pageable pageable);

    /**
     * Count errors for a job.
     */
    long countByJobId(String jobId);

    /**
     * Count errors for a connection.
     */
    long countByConnectionId(String connectionId);

    /**
     * Count errors by type for a job.
     */
    long countByJobIdAndErrorType(String jobId, String errorType);

    /**
     * Delete errors for a job.
     */
    void deleteByJobId(String jobId);

    /**
     * Delete all errors for a connection.
     */
    void deleteByConnectionId(String connectionId);

    /**
     * Delete all errors for an organization.
     */
    void deleteByOrganizationId(String organizationId);

    /**
     * Delete errors older than a specific date.
     */
    void deleteByOccurredAtBefore(LocalDateTime date);
}
package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluenceSyncJob;
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
 * Repository for ConfluenceSyncJob entity.
 */
@Repository
public interface ConfluenceSyncJobRepository extends JpaRepository<ConfluenceSyncJob, String> {

    /**
     * Find all jobs for a connection.
     */
    Page<ConfluenceSyncJob> findByConnectionId(String connectionId, Pageable pageable);

    /**
     * Find all jobs for a connection (non-paginated).
     */
    List<ConfluenceSyncJob> findByConnectionIdOrderByCreatedAtDesc(String connectionId);

    /**
     * Find jobs by status.
     */
    List<ConfluenceSyncJob> findByStatus(String status);

    /**
     * Find jobs by connection and status.
     */
    List<ConfluenceSyncJob> findByConnectionIdAndStatus(String connectionId, String status);

    /**
     * Find most recent job for a connection.
     */
    Optional<ConfluenceSyncJob> findFirstByConnectionIdOrderByCreatedAtDesc(String connectionId);

    /**
     * Find jobs created after a specific date.
     */
    List<ConfluenceSyncJob> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Find running jobs (for monitoring stale jobs).
     */
    @Query("SELECT j FROM ConfluenceSyncJob j WHERE j.status = 'RUNNING' AND j.startedAt < :threshold")
    List<ConfluenceSyncJob> findStaleRunningJobs(@Param("threshold") LocalDateTime threshold);

    /**
     * Count jobs by connection and status.
     */
    long countByConnectionIdAndStatus(String connectionId, String status);

    /**
     * Delete all jobs for a connection.
     */
    void deleteByConnectionId(String connectionId);

    /**
     * Delete all jobs for an organization.
     */
    void deleteByOrganizationId(String organizationId);
}
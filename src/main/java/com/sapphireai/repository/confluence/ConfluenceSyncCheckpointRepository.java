package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluenceSyncCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfluenceSyncCheckpoint entity.
 */
@Repository
public interface ConfluenceSyncCheckpointRepository extends JpaRepository<ConfluenceSyncCheckpoint, String> {

    /**
     * Find all checkpoints for a job.
     */
    List<ConfluenceSyncCheckpoint> findByJobId(String jobId);

    /**
     * Find checkpoint for a job and space.
     */
    Optional<ConfluenceSyncCheckpoint> findByJobIdAndSpaceKey(String jobId, String spaceKey);

    /**
     * Find all checkpoints for a connection.
     */
    List<ConfluenceSyncCheckpoint> findByConnectionId(String connectionId);

    /**
     * Find most recent checkpoint for a connection and space.
     */
    Optional<ConfluenceSyncCheckpoint> findFirstByConnectionIdAndSpaceKeyOrderByUpdatedAtDesc(
            String connectionId, String spaceKey);

    /**
     * Delete checkpoints for a job.
     */
    void deleteByJobId(String jobId);

    /**
     * Delete all checkpoints for a connection.
     */
    void deleteByConnectionId(String connectionId);
}
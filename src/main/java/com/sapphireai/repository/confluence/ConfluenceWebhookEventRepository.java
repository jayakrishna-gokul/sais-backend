package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluenceWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ConfluenceWebhookEvent entity.
 */
@Repository
public interface ConfluenceWebhookEventRepository extends JpaRepository<ConfluenceWebhookEvent, String> {

    /**
     * Find all webhook events for a connection.
     */
    Page<ConfluenceWebhookEvent> findByConnectionId(String connectionId, Pageable pageable);

    /**
     * Find unprocessed webhook events for a connection.
     */
    List<ConfluenceWebhookEvent> findByConnectionIdAndProcessed(String connectionId, boolean processed);

    /**
     * Find webhook events by event type.
     */
    List<ConfluenceWebhookEvent> findByConnectionIdAndEventType(String connectionId, String eventType);

    /**
     * Find webhook events received after a specific date.
     */
    List<ConfluenceWebhookEvent> findByConnectionIdAndReceivedAtAfter(String connectionId, LocalDateTime date);

    /**
     * Find unprocessed events older than a threshold (for retry/cleanup).
     */
    @Query("SELECT w FROM ConfluenceWebhookEvent w WHERE w.processed = false AND w.receivedAt < :threshold")
    List<ConfluenceWebhookEvent> findStaleUnprocessedEvents(@Param("threshold") LocalDateTime threshold);

    /**
     * Count unprocessed events for a connection.
     */
    long countByConnectionIdAndProcessed(String connectionId, boolean processed);

    /**
     * Delete all webhook events for a connection.
     */
    void deleteByConnectionId(String connectionId);

    /**
     * Delete all webhook events for an organization.
     */
    void deleteByOrganizationId(String organizationId);

    /**
     * Delete processed events older than a specific date.
     */
    void deleteByProcessedAndReceivedAtBefore(boolean processed, LocalDateTime date);
}
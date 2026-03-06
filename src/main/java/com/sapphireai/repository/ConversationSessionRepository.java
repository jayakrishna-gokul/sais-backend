package com.sapphireai.repository;

import com.sapphireai.model.entity.ConversationSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Conversation session repository with organization isolation.
 */
@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {

    /**
     * Find all sessions for user in their organization.
     * Used for: Chat history sidebar
     */
    Page<ConversationSession> findByOrganizationIdAndUserIdOrderByLastActivityAtDesc(
            String organizationId,
            String userId,
            Pageable pageable
    );

    /**
     * Find recent active sessions for user.
     * Used for: "Recent chats" quick access
     */
    List<ConversationSession> findTop5ByOrganizationIdAndUserIdOrderByLastActivityAtDesc(
            String organizationId,
            String userId
    );

    /**
     * Find session with security check.
     * Ensures user can only access their own sessions.
     */
    Optional<ConversationSession> findByIdAndOrganizationIdAndUserId(
            String id,
            String organizationId,
            String userId
    );

    /**
     * Find inactive sessions older than threshold.
     * Used for: Cleanup job (archive old sessions)
     */
    List<ConversationSession> findByLastActivityAtBefore(LocalDateTime threshold);

    /**
     * Count active sessions for user.
     * Used for: Limit concurrent sessions per user
     */
    long countByOrganizationIdAndUserIdAndLastActivityAtAfter(
            String organizationId,
            String userId,
            LocalDateTime after
    );

    /**
     * Delete old sessions (batch cleanup).
     */
    @Modifying
    @Query("DELETE FROM ConversationSession s WHERE s.lastActivityAt < :threshold")
    void deleteOldSessions(@Param("threshold") LocalDateTime threshold);
}
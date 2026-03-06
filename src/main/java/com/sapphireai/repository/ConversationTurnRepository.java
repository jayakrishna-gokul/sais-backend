package com.sapphireai.repository;

import com.sapphireai.model.entity.ConversationTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Conversation turn repository for chat history.
 */
@Repository
public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, String> {

    /**
     * Find all turns in a session (ordered chronologically).
     * Used for: Building conversation context for LLM
     */
    List<ConversationTurn> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * Find recent N turns in session.
     * Used for: Context window management (last 5-10 turns)
     */
    List<ConversationTurn> findTop10BySessionIdOrderByTimestampDesc(String sessionId);

    /**
     * Count turns in session.
     * Used for: Determining when to trigger summarization
     */
    long countBySessionId(String sessionId);

    /**
     * Delete all turns for session.
     * Used for: Session cleanup, user deletes chat history
     */
    void deleteBySessionId(String sessionId);

    /**
     * Get average confidence score for session.
     * Used for: Quality metrics, identifying problematic sessions
     */
    @Query("SELECT AVG(t.confidence) FROM ConversationTurn t WHERE t.sessionId = :sessionId")
    Double getAverageConfidence(@Param("sessionId") String sessionId);

    /**
     * Count all turns belonging to an organization.
     *
     * WHY explicit JOIN instead of ct.session:
     * ConversationTurn stores sessionId as a plain String — there is no @ManyToOne
     * association to ConversationSession. Hibernate cannot traverse "ct.session"
     * because no such mapped relationship exists. The JOIN is written against the
     * raw sessionId field to reach ConversationSession and filter by organizationId.
     */
    @Query("""
        SELECT COUNT(ct) FROM ConversationTurn ct
        JOIN ConversationSession cs ON ct.sessionId = cs.id
        WHERE cs.organizationId = :organizationId
        """)
    long countByOrganizationId(@Param("organizationId") String organizationId);
}
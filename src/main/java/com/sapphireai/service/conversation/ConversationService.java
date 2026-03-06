package com.sapphireai.service.conversation;

import com.sapphireai.model.entity.ConversationSession;
import com.sapphireai.model.entity.ConversationTurn;
import com.sapphireai.repository.ConversationSessionRepository;
import com.sapphireai.repository.ConversationTurnRepository;
import com.sapphireai.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conversation management with automatic summarization for long sessions.
 * Handles 15+ turn conversations by summarizing old context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationTurnRepository turnRepository;
    private final LlmService llmService;

    @Value("${sapphire.conversation.summarize-threshold:15}")
    private int summarizeThreshold;

    @Value("${sapphire.conversation.context-token-limit:28000}")
    private int contextTokenLimit;

    /**
     * Create new conversation session.
     */
    @Transactional
    public ConversationSession createSession(String userId, String organizationId, String title) {
        ConversationSession session = ConversationSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .organizationId(organizationId)
                .title(title != null ? title : "New Conversation")
                .turnCount(0)
                .build();

        return sessionRepository.save(session);
    }

    /**
     * Add turn to conversation.
     */
    @Transactional
    public ConversationTurn addTurn(
            String sessionId,
            String query,
            String response,
            Double confidence,
            List<String> sourceChunkIds
    ) {
        ConversationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        ConversationTurn turn = ConversationTurn.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .query(query)
                .response(response)
                .confidence(confidence)
                .sources(String.join(",", sourceChunkIds))
                .build();

        turnRepository.save(turn);

        // Update session
        session.setTurnCount(session.getTurnCount() + 1);
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Turn added to session {}: turn count = {}", sessionId, session.getTurnCount());

        return turn;
    }

    /**
     * Build conversation context for LLM.
     * Automatically summarizes if conversation exceeds threshold.
     */
    public String buildConversationContext(String sessionId, String newQuery) {
        List<ConversationTurn> turns = turnRepository.findBySessionIdOrderByTimestampAsc(sessionId);

        if (turns.isEmpty()) {
            return "";
        }

        // If conversation short, return full context
        if (turns.size() < summarizeThreshold) {
            return buildFullContext(turns, newQuery);
        }

        // Long conversation: summarize old turns, keep recent ones in full
        return buildSummarizedContext(turns, newQuery);
    }

    private String buildFullContext(List<ConversationTurn> turns, String newQuery) {
        String history = turns.stream()
                .map(t -> String.format("User: %s\nAssistant: %s", t.getQuery(), t.getResponse()))
                .collect(Collectors.joining("\n\n"));

        return String.format("""
                Previous conversation:
                %s
                
                Current question: %s
                """, history, newQuery);
    }

    private String buildSummarizedContext(List<ConversationTurn> turns, String newQuery) {
        // Keep last 5 turns in full detail
        int recentTurnsCount = 5;
        List<ConversationTurn> oldTurns = turns.subList(0, Math.max(0, turns.size() - recentTurnsCount));
        List<ConversationTurn> recentTurns = turns.subList(
                Math.max(0, turns.size() - recentTurnsCount),
                turns.size()
        );

        // Summarize old turns
        String oldSummary = summarizeOldTurns(oldTurns);

        // Format recent turns
        String recentHistory = recentTurns.stream()
                .map(t -> String.format("User: %s\nAssistant: %s", t.getQuery(), t.getResponse()))
                .collect(Collectors.joining("\n\n"));

        return String.format("""
                Summary of earlier conversation:
                %s
                
                Recent conversation:
                %s
                
                Current question: %s
                """, oldSummary, recentHistory, newQuery);
    }

    private String summarizeOldTurns(List<ConversationTurn> oldTurns) {
        if (oldTurns.isEmpty()) {
            return "No previous conversation.";
        }

        // Build text of old turns
        String oldConversation = oldTurns.stream()
                .map(t -> String.format("User asked: %s\nAnswered: %s", t.getQuery(), t.getResponse()))
                .collect(Collectors.joining("\n\n"));

        // Ask LLM to summarize
        String summaryPrompt = String.format("""
                Summarize this conversation in 3-5 concise bullet points, focusing on key topics discussed:
                
                %s
                
                Summary (bullet points):
                """, oldConversation);

        try {
            return llmService.generateAnswer(summaryPrompt, List.of());
        } catch (Exception e) {
            log.error("Failed to summarize conversation", e);
            return "Earlier conversation covered multiple topics.";
        }
    }

    /**
     * Get conversation history.
     */
    public List<ConversationTurn> getHistory(String sessionId) {
        return turnRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * Delete old inactive sessions (cleanup job).
     */
    @Transactional
    public void cleanupOldSessions(int daysInactive) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysInactive);
        List<ConversationSession> oldSessions = sessionRepository.findByLastActivityAtBefore(threshold);

        for (ConversationSession session : oldSessions) {
            turnRepository.deleteBySessionId(session.getId());
            sessionRepository.delete(session);
        }

        log.info("Cleaned up {} inactive sessions", oldSessions.size());
    }
}
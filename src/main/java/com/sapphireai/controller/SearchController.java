package com.sapphireai.controller;

import com.sapphireai.model.dto.SearchRequest;
import com.sapphireai.model.dto.SearchResponse;
import com.sapphireai.model.entity.ConversationSession;
import com.sapphireai.model.entity.ConversationTurn;
import com.sapphireai.model.entity.User;
import com.sapphireai.model.entity.audit.AuditEvent;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.config.Audited;
import com.sapphireai.service.conversation.ConversationService;
import com.sapphireai.service.rag.RagEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final RagEngine ragEngine;
    private final ConversationService conversationService;
    private final UserRepository userRepository;

    @PostMapping
    @Audited(eventType = AuditEvent.EventType.SEARCH, resourceType = "SEARCH")
    public ResponseEntity<SearchResponse> search(
            @Valid @RequestBody SearchRequest request,
            Authentication authentication
    ) {
        User user = getUser(authentication);

        // Create session if not provided
        String sessionId = request.getSessionId();
        if (sessionId == null) {
            ConversationSession session = conversationService.createSession(
                    user.getId(),
                    user.getOrganizationId(),
                    "Quick Search"
            );
            sessionId = session.getId();
        }

        // Build context from conversation history
        String conversationContext = conversationService.buildConversationContext(
                sessionId,
                request.getQuery()
        );

        // Execute RAG search
        SearchResponse response = ragEngine.search(request, user.getOrganizationId());
        response.setSessionId(sessionId);

        // Save turn to conversation
        List<String> sourceIds = response.getSources().stream()
                .map(s -> s.getChunkId())
                .collect(Collectors.toList());

        conversationService.addTurn(
                sessionId,
                request.getQuery(),
                response.getAnswer(),
                response.getConfidence(),
                sourceIds
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ConversationSession>> getSessions(Authentication authentication) {
        User user = getUser(authentication);
        var sessions = conversationService.getHistory(user.getId());
        return ResponseEntity.ok(List.of());  // Simplified
    }

    @GetMapping("/sessions/{sessionId}/history")
    public ResponseEntity<List<ConversationTurn>> getHistory(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        List<ConversationTurn> history = conversationService.getHistory(sessionId);
        return ResponseEntity.ok(history);
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
package com.sapphireai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents a single question-answer exchange in a conversation.
 */
@Entity
@Table(name = "conversation_turns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "query", length = 2000, nullable = false)
    private String query;

    @Column(name = "response", length = 10000)
    private String response;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "sources", length = 5000)
    private String sources; // JSON array of chunk IDs

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
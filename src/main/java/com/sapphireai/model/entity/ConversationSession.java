package com.sapphireai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents a conversation session with multiple turns.
 * Enables multi-turn contextual conversations.
 */
@Entity
@Table(name = "conversation_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(name = "title")
    private String title;

    @Column(name = "turn_count")
    private Integer turnCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastActivityAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActivityAt = LocalDateTime.now();
    }
}

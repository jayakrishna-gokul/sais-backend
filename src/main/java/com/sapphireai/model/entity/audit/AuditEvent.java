package com.sapphireai.model.entity.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user", columnList = "user_email"),
        @Index(name = "idx_audit_time", columnList = "timestamp"),
        @Index(name = "idx_audit_org", columnList = "organization_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who
    @Column(nullable = false, length = 255)
    private String userEmail;

    @Column(name = "organization_id", length = 36)
    private String organizationId;

    // What
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType eventType;

    @Column(length = 255)
    private String resourceType;       // e.g. "DOCUMENT", "SEARCH", "USER"

    @Column(length = 255)
    private String resourceId;         // e.g. document UUID

    // Where / How
    @Column(length = 500)
    private String action;             // e.g. "POST /api/search"

    @Column(length = 45)
    private String ipAddress;

    // Result
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventResult result;        // SUCCESS or FAILURE

    @Column(columnDefinition = "TEXT")
    private String details;            // Extra context (search query, error message, etc.)

    // When
    @Column(nullable = false)
    private Instant timestamp;

    public enum EventType {
        AUTH,           // login, logout, register
        SEARCH,         // search queries
        DOCUMENT,       // upload, delete
        CONNECTOR,      // sync, configure
        ADMIN,          // user management, config changes
        SECURITY        // rate limit hit, unauthorized access attempt
    }

    public enum EventResult {
        SUCCESS,
        FAILURE
    }
}

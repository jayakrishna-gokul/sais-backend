package com.sapphireai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Organization (company/tenant) entity for multi-tenancy.
 * Each company using Sapphire AI is an organization.
 * All users and documents belong to an organization.
 */
@Entity
@Table(name = "organizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;  // "ACME Corporation"

    /**
     * Email domain for auto-enrollment.
     * Users with @acme.com automatically join this org.
     */
    @Column(unique = true, length = 255)
    private String domain;  // "acme.com"

    /**
     * SSO provider: "google", "okta", "azure", null for email/password
     */
    @Column(name = "sso_provider", length = 50)
    private String ssoProvider;

    /**
     * JSON configuration for SSO (client ID, secret, endpoints)
     */
    @Column(name = "sso_config", columnDefinition = "TEXT")
    private String ssoConfig;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
package com.sapphireai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * User entity for authentication and authorization.
 * Stores user credentials and profile information.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt hashed

    private String fullName;
    private String organization;
    private String role = "USER"; // USER or ADMIN
    private Boolean enabled = true;

    /**
     * Organization ID - links user to their company.
     * All users in same organization see same documents.
     * Example: "acme-corp", "tech-startup-inc"
     */
    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
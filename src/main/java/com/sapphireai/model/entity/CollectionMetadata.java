package com.sapphireai.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks embedding model metadata for vector collections.
 * Prevents dimension mismatches when switching embedding providers.
 */
@Entity
@Table(name = "collection_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, name = "collection_name")
    private String collectionName;

    @Column(nullable = false, name = "embedding_provider")
    private String embeddingProvider;

    @Column(nullable = false, name = "embedding_model")
    private String embeddingModel;

    @Column(nullable = false)
    private Integer dimension;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "updated_at")
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
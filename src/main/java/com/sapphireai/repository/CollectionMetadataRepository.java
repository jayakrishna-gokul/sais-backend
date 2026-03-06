package com.sapphireai.repository;

import com.sapphireai.model.entity.CollectionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionMetadataRepository extends JpaRepository<CollectionMetadata, UUID> {

    Optional<CollectionMetadata> findByCollectionName(String collectionName);

    boolean existsByCollectionName(String collectionName);
}
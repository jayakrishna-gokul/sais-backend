package com.sapphireai.repository.confluence;

import com.sapphireai.model.entity.confluence.ConfluenceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfluenceConnection entity.
 */
@Repository
public interface ConfluenceConnectionRepository extends JpaRepository<ConfluenceConnection, String> {

    /**
     * Find all connections for an organization.
     */
    List<ConfluenceConnection> findByOrganizationId(String organizationId);

    /**
     * Find enabled connections for an organization.
     */
    List<ConfluenceConnection> findByOrganizationIdAndEnabled(String organizationId, boolean enabled);

    /**
     * Find connection by organization and connection name.
     */
    Optional<ConfluenceConnection> findByOrganizationIdAndConnectionName(String organizationId, String connectionName);

    /**
     * Find connection by organization and baseUrl (for duplicate detection).
     * Used to prevent duplicate connections to the same Confluence instance.
     */
    Optional<ConfluenceConnection> findByOrganizationIdAndBaseUrlAndEnabled(
            String organizationId,
            String baseUrl,
            boolean enabled
    );

    /**
     * Check if connection exists for organization and base URL.
     */
    boolean existsByOrganizationIdAndBaseUrl(String organizationId, String baseUrl);

    /**
     * Delete all connections for an organization.
     */
    void deleteByOrganizationId(String organizationId);

    Optional<ConfluenceConnection> findFirstByOrganizationId(String organizationId);

}
package com.sapphireai.repository;

import com.sapphireai.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Organization repository for multi-tenancy.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {

    /**
     * Find organization by email domain.
     * Used for auto-enrollment: user@acme.com → finds "acme.com" org
     */
    Optional<Organization> findByDomain(String domain);

    /**
     * Check if domain already exists (prevent duplicates)
     */
    boolean existsByDomain(String domain);
}
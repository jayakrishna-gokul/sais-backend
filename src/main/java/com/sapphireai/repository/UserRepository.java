package com.sapphireai.repository;

import com.sapphireai.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * User repository with organization isolation.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by email (for login)
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if email already registered
     */
    boolean existsByEmail(String email);

    /**
     * Find all users in an organization (admin function)
     */
    Page<User> findByOrganizationId(String organizationId, Pageable pageable);

    /**
     * Count users in organization
     */
    long countByOrganizationId(String organizationId);
}
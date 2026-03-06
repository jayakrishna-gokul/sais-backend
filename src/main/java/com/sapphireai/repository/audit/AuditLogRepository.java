package com.sapphireai.repository.audit;

import com.sapphireai.model.entity.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditEvent, Long> {

    // Get audit trail for an organization (what customers will want to see)
    @Query("""
            SELECT a FROM AuditEvent a
            WHERE a.organizationId = :orgId
            AND a.timestamp BETWEEN :from AND :to
            ORDER BY a.timestamp DESC
            LIMIT 500
            """)
    List<AuditEvent> findByOrganizationAndTimeRange(
            @Param("orgId") String orgId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Get audit trail for a specific user
    List<AuditEvent> findByUserEmailOrderByTimestampDesc(String email);

    // Cleanup old logs (retention policy)
    @Query("""
            DELETE FROM AuditEvent a
            WHERE a.timestamp < :cutoff
            """)
    long deleteByTimestampBefore(@Param("cutoff") Instant cutoff);

    long countByOrganizationId(String organizationId);
}
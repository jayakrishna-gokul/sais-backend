package com.sapphireai.service.audit;

import com.sapphireai.model.entity.audit.AuditEvent;
import com.sapphireai.repository.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Value("${sapphire.audit.retention-days:90}")
    private int retentionDays;

    // ─── Write audit event ───
    @Transactional
    public void log(AuditEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        auditLogRepository.save(event);
    }

    // ─── Query audit trail for an organization ───
    @Transactional(readOnly = true)
    public List<AuditEvent> getAuditTrail(String organizationId, Instant from, Instant to) {
        return auditLogRepository.findByOrganizationAndTimeRange(organizationId, from, to);
    }

    // ─── Cleanup old logs based on retention policy ───
    @Scheduled(cron = "0 0 2 * * ?")  // Every night at 2 AM
    @Transactional
    public void cleanupOldLogs() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86400L);
        long deleted = auditLogRepository.deleteByTimestampBefore(cutoff);
        if (deleted > 0) {
            log.info("Audit cleanup: deleted {} logs older than {} days", deleted, retentionDays);
        }
    }
}
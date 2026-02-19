package com.example.BusinessLoanAPISpringBoot.audit.service;

import com.example.BusinessLoanAPISpringBoot.audit.model.AuditEvent;
import com.example.BusinessLoanAPISpringBoot.audit.repo.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit event writer/query service.
 *
 * Notes:
 * - `details` is persisted as JSONB (String in Java).
 * - We keep the schema intentionally generic so many parts of the app can reuse it.
 */
@Service
public class AuditEventService {

    private final AuditEventRepository repo;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    // PUBLIC_INTERFACE
    @Transactional
    public AuditEvent writeEvent(
            String eventType,
            UUID actorUserId,
            String correlationId,
            String ipAddress,
            String userAgent,
            String outcome,
            Map<String, Object> details
    ) {
        /** Persist an audit event to the `audit_event` table. */
        Instant now = Instant.now();
        AuditEvent event = new AuditEvent()
                .setId(UUID.randomUUID())
                .setEventType(eventType)
                .setUserId(actorUserId)
                .setCorrelationId(correlationId)
                .setIpAddress(ipAddress)
                .setUserAgent(userAgent)
                .setOutcome(outcome == null ? "SUCCESS" : outcome)
                .setDetails(serializeDetails(details))
                .setCreatedAt(now);

        return repo.save(event);
    }

    // PUBLIC_INTERFACE
    @Transactional(readOnly = true)
    public Page<AuditEvent> query(
            Instant fromInclusive,
            Instant toInclusive,
            String eventType,
            UUID userId,
            Pageable pageable
    ) {
        /** Query audit events using optional filters for date range, event type, and user id. */
        Instant from = fromInclusive == null ? Instant.EPOCH : fromInclusive;
        Instant to = toInclusive == null ? Instant.now() : toInclusive;

        if (eventType != null && !eventType.isBlank() && userId != null) {
            return repo.findAllByEventTypeAndUserIdAndCreatedAtBetween(eventType, userId, from, to, pageable);
        }
        if (eventType != null && !eventType.isBlank()) {
            return repo.findAllByEventTypeAndCreatedAtBetween(eventType, from, to, pageable);
        }
        if (userId != null) {
            return repo.findAllByUserIdAndCreatedAtBetween(userId, from, to, pageable);
        }
        return repo.findAllByCreatedAtBetween(from, to, pageable);
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            // Don't fail the primary business action because audit serialization failed.
            return "{\"error\":\"failed_to_serialize_details\"}";
        }
    }
}

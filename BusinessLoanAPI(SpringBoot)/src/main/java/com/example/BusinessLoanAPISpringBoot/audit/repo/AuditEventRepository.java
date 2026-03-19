package com.example.BusinessLoanAPISpringBoot.audit.repo;

import com.example.BusinessLoanAPISpringBoot.audit.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for audit log events.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByCreatedAtBetween(Instant fromInclusive, Instant toInclusive, Pageable pageable);

    Page<AuditEvent> findAllByUserIdAndCreatedAtBetween(UUID userId, Instant fromInclusive, Instant toInclusive, Pageable pageable);

    Page<AuditEvent> findAllByEventTypeAndCreatedAtBetween(String eventType, Instant fromInclusive, Instant toInclusive, Pageable pageable);

    Page<AuditEvent> findAllByEventTypeAndUserIdAndCreatedAtBetween(
            String eventType,
            UUID userId,
            Instant fromInclusive,
            Instant toInclusive,
            Pageable pageable
    );
}

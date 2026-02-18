package com.example.BusinessLoanAPISpringBoot.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted audit log event.
 *
 * Backed by the existing `audit_event` table created in V1__init_auth_schema.sql.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    /**
     * Expected values: SUCCESS / FAILURE (free-form but standardized in our service layer).
     */
    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    /**
     * JSON payload stored as JSONB in Postgres; mapped as String to avoid extra dependencies.
     */
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public AuditEvent setId(UUID id) {
        this.id = id;
        return this;
    }

    public String getEventType() {
        return eventType;
    }

    public AuditEvent setEventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuditEvent setUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public AuditEvent setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public AuditEvent setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public AuditEvent setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public String getOutcome() {
        return outcome;
    }

    public AuditEvent setOutcome(String outcome) {
        this.outcome = outcome;
        return this;
    }

    public String getDetails() {
        return details;
    }

    public AuditEvent setDetails(String details) {
        this.details = details;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AuditEvent setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}

package com.example.BusinessLoanAPISpringBoot.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Loan application draft persisted per user, supporting multi-step wizard saves.
 *
 * Stores a flexible JSON payload (data) and a section-level status map (sectionStatus).
 */
@Entity
@Table(name = "loan_application_draft")
public class LoanApplicationDraft {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * JSON payload representing the overall draft state.
     * Stored as JSONB in Postgres; mapped as String to avoid extra dependencies.
     */
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private String data;

    /**
     * JSON object mapping section keys to status metadata.
     * Example: {"businessInfo":{"state":"COMPLETED","updatedAt":"..."}} or a simpler string map.
     */
    @Column(name = "section_status", nullable = false, columnDefinition = "jsonb")
    private String sectionStatus;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Optimistic concurrency token. Increments on each update.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public LoanApplicationDraft setId(UUID id) {
        this.id = id;
        return this;
    }

    public UUID getUserId() {
        return userId;
    }

    public LoanApplicationDraft setUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public String getData() {
        return data;
    }

    public LoanApplicationDraft setData(String data) {
        this.data = data;
        return this;
    }

    public String getSectionStatus() {
        return sectionStatus;
    }

    public LoanApplicationDraft setSectionStatus(String sectionStatus) {
        this.sectionStatus = sectionStatus;
        return this;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public LoanApplicationDraft setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public LoanApplicationDraft setStatus(String status) {
        this.status = status;
        return this;
    }

    public Long getVersion() {
        return version;
    }

    public LoanApplicationDraft setVersion(Long version) {
        this.version = version;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LoanApplicationDraft setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LoanApplicationDraft setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}

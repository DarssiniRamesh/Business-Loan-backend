package com.example.BusinessLoanAPISpringBoot.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;


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
     * Computed automated risk score (0-100). Null until the scoring engine runs.
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * Automated decision outcome derived from risk score and business rules.
     * Expected values: PRE_QUALIFIED, MANUAL_REVIEW, DECLINED.
     */
    @Column(name = "decision")
    private String decision;

    /**
     * Human-readable explanation of the decision, suitable for audit/debugging.
     */
    @Column(name = "decision_reason")
    private String decisionReason;

    /**
     * When the application was last evaluated by the risk engine.
     */
    @Column(name = "decisioned_at")
    private Instant decisionedAt;

    /**
     * Timestamp when the draft was formally submitted.
     * After submission, the draft is locked (immutable) and cannot be updated/patched/deleted.
     */
    @Column(name = "submitted_at")
    private Instant submittedAt;

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

    public Integer getRiskScore() {
        return riskScore;
    }

    public LoanApplicationDraft setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
        return this;
    }

    public String getDecision() {
        return decision;
    }

    public LoanApplicationDraft setDecision(String decision) {
        this.decision = decision;
        return this;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public LoanApplicationDraft setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
        return this;
    }

    public Instant getDecisionedAt() {
        return decisionedAt;
    }

    public LoanApplicationDraft setDecisionedAt(Instant decisionedAt) {
        this.decisionedAt = decisionedAt;
        return this;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public LoanApplicationDraft setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
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

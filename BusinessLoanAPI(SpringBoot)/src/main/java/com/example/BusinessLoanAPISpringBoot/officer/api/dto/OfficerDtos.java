package com.example.BusinessLoanAPISpringBoot.officer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for officer-facing APIs.
 */
public class OfficerDtos {

    @Schema(description = "Officer queue row summarizing a submitted application.")
    public record QueueItem(
            @Schema(description = "Loan draft id (acts as application id).")
            UUID draftId,

            @Schema(description = "Applicant (user) id.")
            UUID applicantUserId,

            @Schema(description = "Application status (e.g. SUBMITTED).")
            String status,

            @Schema(description = "Risk score computed by automated engine, if present.")
            Integer riskScore,

            @Schema(description = "Automated decision (PRE_QUALIFIED / MANUAL_REVIEW / DECLINED) if present.")
            String decision,

            @Schema(description = "When the application was submitted.")
            Instant submittedAt,

            @Schema(description = "Last updated timestamp.")
            Instant updatedAt
    ) {}

    @Schema(description = "Officer application detail view.")
    public record ApplicationDetail(
            @Schema(description = "Loan draft id (acts as application id).")
            UUID draftId,

            @Schema(description = "Applicant (user) id.")
            UUID applicantUserId,

            @Schema(description = "JSON draft payload.")
            String data,

            @Schema(description = "JSON section status map.")
            String sectionStatus,

            @Schema(description = "Wizard status / application status.")
            String status,

            Integer riskScore,
            String decision,
            String decisionReason,
            Instant decisionedAt,
            Instant submittedAt,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @Schema(description = "Request body to override an automated decision.")
    public record DecisionOverrideRequest(
            @NotNull
            @Schema(description = "New decision outcome (PRE_QUALIFIED / MANUAL_REVIEW / DECLINED).", example = "MANUAL_REVIEW")
            String decision,

            @NotBlank
            @Schema(description = "Officer justification / note for the override (required).")
            String reason
    ) {}

    @Schema(description = "Response returned after an override is applied.")
    public record DecisionOverrideResponse(
            UUID draftId,
            String previousDecision,
            String newDecision,
            String overrideReason,
            Instant overriddenAt
    ) {}

    @Schema(description = "Audit log event response.")
    public record AuditEventResponse(
            UUID id,
            String eventType,
            UUID userId,
            String correlationId,
            String ipAddress,
            String userAgent,
            String outcome,
            String details,
            Instant createdAt
    ) {}
}

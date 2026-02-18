package com.example.BusinessLoanAPISpringBoot.loan.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for loan application draft CRUD and section patch operations.
 */
public final class LoanDraftDtos {

    private LoanDraftDtos() {}

    @Schema(name = "LoanDraftCreateRequest", description = "Request payload to create a new loan application draft.")
    public record CreateRequest(
            @Schema(description = "Initial draft JSON data. Must be a JSON object encoded as a string.", example = "{\"business\":{}}")
            @NotNull
            String data,

            @Schema(description = "Initial section status JSON object encoded as a string.", example = "{\"businessInfo\":\"IN_PROGRESS\"}")
            String sectionStatus,

            @Schema(description = "Current wizard step identifier.", example = "businessInfo")
            String currentStep
    ) {}

    @Schema(name = "LoanDraftUpdateRequest", description = "Full update payload for a loan application draft. Replaces stored fields.")
    public record UpdateRequest(
            @Schema(description = "Draft JSON data encoded as a string.", example = "{\"business\":{},\"owner\":{}}")
            @NotNull
            String data,

            @Schema(description = "Section status JSON encoded as a string.", example = "{\"businessInfo\":\"COMPLETED\",\"ownerInfo\":\"IN_PROGRESS\"}")
            String sectionStatus,

            @Schema(description = "Current wizard step identifier.", example = "ownerInfo")
            String currentStep,

            @Schema(description = "Draft status.", example = "DRAFT")
            String status,

            @Schema(description = "Expected current version for optimistic concurrency. If provided and mismatched, request fails with 409.")
            Long expectedVersion
    ) {}

    @Schema(name = "LoanDraftSectionPatchRequest", description = "Partial update payload for a specific wizard section. Merges into existing draft data.")
    public record SectionPatchRequest(
            @Schema(description = "Section key identifier.", example = "businessInfo")
            @NotBlank
            String sectionKey,

            @Schema(description = "Section JSON fragment encoded as a string. Must be a JSON object string; it will be merged into draft.data under sectionKey.")
            @NotNull
            String sectionData,

            @Schema(description = "Optional section status JSON fragment encoded as a string. Will be merged into draft.sectionStatus under sectionKey.")
            String sectionStatus,

            @Schema(description = "Optional updated current step identifier.", example = "ownerInfo")
            String currentStep,

            @Schema(description = "Expected current version for optimistic concurrency. If provided and mismatched, request fails with 409.")
            Long expectedVersion
    ) {}

    @Schema(name = "LoanDraftResponse", description = "Loan application draft response model.")
    public record DraftResponse(
            @Schema(description = "Draft ID.")
            UUID id,
            @Schema(description = "Owning user ID.")
            UUID userId,
            @Schema(description = "Draft JSON data encoded as a string.")
            String data,
            @Schema(description = "Section status JSON encoded as a string.")
            String sectionStatus,
            @Schema(description = "Current wizard step identifier.")
            String currentStep,
            @Schema(description = "Draft status.")
            String status,
            @Schema(description = "Optimistic concurrency version.")
            Long version,
            @Schema(description = "Created timestamp.")
            Instant createdAt,
            @Schema(description = "Updated timestamp.")
            Instant updatedAt
    ) {}
}

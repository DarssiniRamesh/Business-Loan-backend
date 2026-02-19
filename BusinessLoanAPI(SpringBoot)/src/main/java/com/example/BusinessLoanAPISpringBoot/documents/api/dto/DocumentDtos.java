package com.example.BusinessLoanAPISpringBoot.documents.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for supporting document APIs.
 */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    @Schema(name = "DocumentUploadResponse")
    public record UploadResponse(
            @Schema(description = "Document id") UUID id,
            @Schema(description = "Owning user id") UUID userId,
            @Schema(description = "Optional loan draft id associated with this document") UUID loanDraftId,
            @Schema(description = "Original filename supplied by client") String originalFilename,
            @Schema(description = "Detected/declared content type") String contentType,
            @Schema(description = "File size in bytes") long sizeBytes,
            @Schema(description = "SHA-256 checksum (hex)") String sha256Hex,
            @Schema(description = "JSON metadata stored alongside the document") String metadata,
            @Schema(description = "Created timestamp") Instant createdAt
    ) {
    }

    @Schema(name = "DocumentListItem")
    public record ListItem(
            UUID id,
            UUID userId,
            UUID loanDraftId,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256Hex,
            String metadata,
            Instant createdAt
    ) {
    }

    @Schema(name = "DocumentMetadataUpdateRequest")
    public record MetadataUpdateRequest(
            @Schema(
                    description = "JSON object string containing metadata to store (replaces the stored metadata).",
                    example = "{\"docType\":\"BANK_STATEMENT\",\"period\":\"2024-12\"}"
            )
            @NotBlank(message = "metadata is required")
            String metadata
    ) {
    }
}

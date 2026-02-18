package com.example.BusinessLoanAPISpringBoot.documents.api;

import com.example.BusinessLoanAPISpringBoot.documents.api.dto.DocumentDtos;
import com.example.BusinessLoanAPISpringBoot.documents.service.SupportingDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Supporting document endpoints (secure upload/list/download/delete).
 *
 * Authentication:
 * - Requires JWT Bearer token.
 * - Uses JWT subject as the user id (UUID string matching app_user.id).
 *
 * Storage strategy:
 * - Metadata: Postgres (supporting_document table)
 * - Content: filesystem under app.documents.storage-root
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Secure supporting document upload/list/download/delete")
public class SupportingDocumentController {

    private final SupportingDocumentService service;

    public SupportingDocumentController(SupportingDocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a supporting document",
            description = "Uploads a PDF/JPG/PNG for the authenticated user and persists metadata. Optional loanDraftId links the document to a draft."
    )
    public DocumentDtos.UploadResponse upload(
            Authentication auth,
            @Parameter(description = "Optional loan draft id to associate with the document")
            @RequestParam(name = "loanDraftId", required = false) UUID loanDraftId,
            @Parameter(description = "Optional JSON object string for metadata (default: {})")
            @RequestParam(name = "metadata", required = false) String metadata,
            @Parameter(description = "File to upload (PDF/JPG/PNG). Must be <= configured max bytes.")
            @RequestPart("file") MultipartFile file
    ) {
        UUID userId = subjectAsUserId(auth);
        return service.upload(userId, loanDraftId, file, metadata);
    }

    @GetMapping
    @Operation(
            summary = "List supporting documents",
            description = "Lists documents belonging to the authenticated user. Optional loanDraftId filters results."
    )
    public List<DocumentDtos.ListItem> list(
            Authentication auth,
            @RequestParam(name = "loanDraftId", required = false) UUID loanDraftId
    ) {
        UUID userId = subjectAsUserId(auth);
        return service.list(userId, loanDraftId);
    }

    @PutMapping("/{documentId}/metadata")
    @Operation(
            summary = "Replace document metadata",
            description = "Replaces the stored metadata JSON for a document owned by the authenticated user."
    )
    public DocumentDtos.ListItem updateMetadata(
            Authentication auth,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentDtos.MetadataUpdateRequest req
    ) {
        UUID userId = subjectAsUserId(auth);
        return service.updateMetadata(userId, documentId, req.metadata());
    }

    @GetMapping("/{documentId}/download")
    @Operation(
            summary = "Download a supporting document",
            description = "Downloads the raw document bytes if the document is owned by the authenticated user."
    )
    public ResponseEntity<Resource> download(Authentication auth, @PathVariable UUID documentId) {
        UUID userId = subjectAsUserId(auth);
        SupportingDocumentService.ResourceDownload dl = service.getDownload(userId, documentId);

        String encodedFilename = URLEncoder.encode(dl.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "attachment; filename*=UTF-8''" + encodedFilename;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .contentLength(dl.sizeBytes())
                .body(dl.resource());
    }

    @DeleteMapping("/{documentId}")
    @Operation(
            summary = "Delete a supporting document",
            description = "Deletes the document metadata and stored bytes if owned by the authenticated user."
    )
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID documentId) {
        UUID userId = subjectAsUserId(auth);
        service.delete(userId, documentId);
        return ResponseEntity.noContent().build();
    }

    private UUID subjectAsUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalArgumentException("Unauthenticated");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            throw new IllegalArgumentException("JWT subject must be a UUID user id");
        }
    }
}

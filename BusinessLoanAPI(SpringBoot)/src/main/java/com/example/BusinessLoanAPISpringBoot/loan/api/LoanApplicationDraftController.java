package com.example.BusinessLoanAPISpringBoot.loan.api;

import com.example.BusinessLoanAPISpringBoot.loan.api.dto.LoanDraftDtos;
import com.example.BusinessLoanAPISpringBoot.loan.service.LoanApplicationDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Loan application draft endpoints for the applicant wizard.
 *
 * Authentication:
 * - Requires JWT Bearer token.
 * - Uses JWT subject as the user id (UUID string matching app_user.id).
 */
@RestController
@RequestMapping("/api/loan/drafts")
@Tag(name = "Loan Drafts", description = "CRUD APIs for loan application drafts and wizard step patching")
public class LoanApplicationDraftController {

    private final LoanApplicationDraftService service;

    public LoanApplicationDraftController(LoanApplicationDraftService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a loan application draft",
            description = "Creates a new draft for the authenticated user."
    )
    public LoanDraftDtos.DraftResponse create(Authentication auth, @Valid @RequestBody LoanDraftDtos.CreateRequest req) {
        UUID userId = subjectAsUserId(auth);
        return service.create(userId, req);
    }

    @GetMapping
    @Operation(
            summary = "List loan application drafts",
            description = "Lists drafts belonging to the authenticated user, ordered by most recent update."
    )
    public List<LoanDraftDtos.DraftResponse> list(Authentication auth) {
        UUID userId = subjectAsUserId(auth);
        return service.list(userId);
    }

    @GetMapping("/{draftId}")
    @Operation(
            summary = "Get a loan application draft",
            description = "Returns a single draft by id, only if owned by the authenticated user."
    )
    public LoanDraftDtos.DraftResponse get(Authentication auth, @PathVariable UUID draftId) {
        UUID userId = subjectAsUserId(auth);
        return service.get(userId, draftId);
    }

    @PutMapping("/{draftId}")
    @Operation(
            summary = "Update a loan application draft",
            description = "Replaces draft fields (data/sectionStatus/currentStep/status). Optionally enforces optimistic concurrency via expectedVersion."
    )
    public LoanDraftDtos.DraftResponse update(
            Authentication auth,
            @PathVariable UUID draftId,
            @Valid @RequestBody LoanDraftDtos.UpdateRequest req
    ) {
        UUID userId = subjectAsUserId(auth);
        return service.update(userId, draftId, req);
    }

    @PatchMapping("/{draftId}/sections")
    @Operation(
            summary = "Patch a wizard section within a draft",
            description = "Partially updates a draft by setting/merging the given section JSON under the provided sectionKey. Optionally updates section status and currentStep."
    )
    public LoanDraftDtos.DraftResponse patchSection(
            Authentication auth,
            @PathVariable UUID draftId,
            @Valid @RequestBody LoanDraftDtos.SectionPatchRequest req
    ) {
        UUID userId = subjectAsUserId(auth);
        return service.patchSection(userId, draftId, req);
    }

    @DeleteMapping("/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a loan application draft",
            description = "Deletes a draft by id if owned by the authenticated user."
    )
    public void delete(Authentication auth, @PathVariable UUID draftId) {
        UUID userId = subjectAsUserId(auth);
        service.delete(userId, draftId);
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

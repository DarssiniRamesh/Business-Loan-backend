package com.example.BusinessLoanAPISpringBoot.loan.service;

import com.example.BusinessLoanAPISpringBoot.loan.api.dto.LoanDraftDtos;
import com.example.BusinessLoanAPISpringBoot.loan.model.LoanApplicationDraft;
import com.example.BusinessLoanAPISpringBoot.loan.repo.LoanApplicationDraftRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for loan application drafts (multi-step wizard persistence).
 */
@Service
public class LoanApplicationDraftService {

    private final LoanApplicationDraftRepository repo;
    private final ObjectMapper objectMapper;
    private final RiskDecisioningService riskDecisioningService;

    public LoanApplicationDraftService(
            LoanApplicationDraftRepository repo,
            ObjectMapper objectMapper,
            RiskDecisioningService riskDecisioningService
    ) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.riskDecisioningService = riskDecisioningService;
    }

    // PUBLIC_INTERFACE
    @Transactional
    public LoanDraftDtos.DraftResponse create(UUID userId, LoanDraftDtos.CreateRequest req) {
        /** Create a new draft for the current user. */
        requireJsonObjectString(req.data(), "data");
        if (req.sectionStatus() != null) {
            requireJsonObjectString(req.sectionStatus(), "sectionStatus");
        }

        Instant now = Instant.now();
        LoanApplicationDraft draft = new LoanApplicationDraft()
                .setId(UUID.randomUUID())
                .setUserId(userId)
                .setData(normalizeJsonObjectString(req.data()))
                .setSectionStatus(req.sectionStatus() == null ? "{}" : normalizeJsonObjectString(req.sectionStatus()))
                .setCurrentStep(req.currentStep())
                .setStatus("DRAFT")
                .setCreatedAt(now)
                .setUpdatedAt(now);

        LoanApplicationDraft saved = repo.save(draft);
        return toResponse(saved);
    }

    // PUBLIC_INTERFACE
    @Transactional(readOnly = true)
    public LoanDraftDtos.DraftResponse get(UUID userId, UUID draftId) {
        /** Read a draft owned by the current user. */
        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        return toResponse(draft);
    }

    // PUBLIC_INTERFACE
    @Transactional(readOnly = true)
    public List<LoanDraftDtos.DraftResponse> list(UUID userId) {
        /** List drafts for the current user, ordered by most recent update first. */
        return repo.findAllByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    // PUBLIC_INTERFACE
    @Transactional
    public LoanDraftDtos.DraftResponse update(UUID userId, UUID draftId, LoanDraftDtos.UpdateRequest req) {
        /** Full update of a draft (replaces stored fields). */
        requireJsonObjectString(req.data(), "data");
        if (req.sectionStatus() != null) {
            requireJsonObjectString(req.sectionStatus(), "sectionStatus");
        }

        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        enforceExpectedVersionIfPresent(draft, req.expectedVersion());

        draft.setData(normalizeJsonObjectString(req.data()));
        if (req.sectionStatus() != null) {
            draft.setSectionStatus(normalizeJsonObjectString(req.sectionStatus()));
        }
        if (req.currentStep() != null) {
            draft.setCurrentStep(req.currentStep());
        }
        if (req.status() != null) {
            draft.setStatus(req.status());
        }
        draft.setUpdatedAt(Instant.now());

        try {
            return toResponse(repo.save(draft));
        } catch (OptimisticLockException ex) {
            throw new IllegalArgumentException("Draft update conflict. Please refresh and retry.");
        }
    }

    // PUBLIC_INTERFACE
    @Transactional
    public LoanDraftDtos.DraftResponse patchSection(UUID userId, UUID draftId, LoanDraftDtos.SectionPatchRequest req) {
        /** Patch a single section within the draft (partial update) by merging JSON objects. */
        requireJsonObjectString(req.sectionData(), "sectionData");
        if (req.sectionStatus() != null) {
            requireJsonObjectString(req.sectionStatus(), "sectionStatus");
        }

        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        enforceExpectedVersionIfPresent(draft, req.expectedVersion());

        // Merge sectionData into data under sectionKey
        ObjectNode dataObj = parseObjectOrThrow(draft.getData(), "stored data");
        ObjectNode sectionObj = parseObjectOrThrow(req.sectionData(), "sectionData");
        dataObj.set(req.sectionKey(), sectionObj);

        draft.setData(writeJson(dataObj));

        // Merge section status under sectionKey (store as object if provided, otherwise no change)
        if (req.sectionStatus() != null) {
            ObjectNode statusObj = parseObjectOrThrow(draft.getSectionStatus(), "stored sectionStatus");
            ObjectNode sectionStatusObj = parseObjectOrThrow(req.sectionStatus(), "sectionStatus");
            statusObj.set(req.sectionKey(), sectionStatusObj);
            draft.setSectionStatus(writeJson(statusObj));
        }

        if (req.currentStep() != null) {
            draft.setCurrentStep(req.currentStep());
        }
        draft.setUpdatedAt(Instant.now());

        try {
            return toResponse(repo.save(draft));
        } catch (OptimisticLockException ex) {
            throw new IllegalArgumentException("Draft update conflict. Please refresh and retry.");
        }
    }

    // PUBLIC_INTERFACE
    @Transactional
    public LoanDraftDtos.DraftResponse runDecisioning(UUID userId, UUID draftId) {
        /** Compute and persist risk score + decision for the given draft owned by the current user. */
        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        RiskDecisioningService.RiskDecision rd = riskDecisioningService.scoreAndDecide(draft.getData());

        draft.setRiskScore(rd.score());
        draft.setDecision(rd.decision().name());
        draft.setDecisionReason(rd.reason());
        draft.setDecisionedAt(Instant.now());

        // Optionally also update status to reflect completion of automated decisioning.
        // Keep existing status if caller wants to control it, but default behavior is helpful.
        if (draft.getStatus() == null || "DRAFT".equalsIgnoreCase(draft.getStatus())) {
            draft.setStatus("DECISIONED");
        }

        draft.setUpdatedAt(Instant.now());
        return toResponse(repo.save(draft));
    }

    // PUBLIC_INTERFACE
    @Transactional
    public void delete(UUID userId, UUID draftId) {
        /** Delete a draft owned by the current user. */
        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        repo.delete(draft);
    }

    private void enforceExpectedVersionIfPresent(LoanApplicationDraft draft, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!expectedVersion.equals(draft.getVersion())) {
            // Use IllegalArgumentException for current error handler; callers interpret as 400.
            // If later extended, map to 409 in a dedicated exception handler.
            throw new IllegalArgumentException("Version mismatch. Expected=" + expectedVersion + " actual=" + draft.getVersion());
        }
    }

    private LoanDraftDtos.DraftResponse toResponse(LoanApplicationDraft d) {
        return new LoanDraftDtos.DraftResponse(
                d.getId(),
                d.getUserId(),
                d.getData(),
                d.getSectionStatus(),
                d.getCurrentStep(),
                d.getStatus(),
                d.getRiskScore(),
                d.getDecision(),
                d.getDecisionReason(),
                d.getDecisionedAt(),
                d.getVersion(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private void requireJsonObjectString(String json, String fieldName) {
        if (json == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        parseObjectOrThrow(json, fieldName);
    }

    private ObjectNode parseObjectOrThrow(String json, String fieldName) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(fieldName + " must be a JSON object string");
            }
            return (ObjectNode) node;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON: " + e.getOriginalMessage());
        }
    }

    private String normalizeJsonObjectString(String json) {
        ObjectNode obj = parseObjectOrThrow(json, "json");
        return writeJson(obj);
    }

    private String writeJson(ObjectNode obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON");
        }
    }
}

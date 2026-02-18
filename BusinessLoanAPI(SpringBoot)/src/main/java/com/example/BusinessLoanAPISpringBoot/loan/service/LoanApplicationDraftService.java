package com.example.BusinessLoanAPISpringBoot.loan.service;

import com.example.BusinessLoanAPISpringBoot.documents.model.SupportingDocument;
import com.example.BusinessLoanAPISpringBoot.documents.repo.SupportingDocumentRepository;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for loan application drafts (multi-step wizard persistence).
 */
@Service
public class LoanApplicationDraftService {

    private final LoanApplicationDraftRepository repo;
    private final SupportingDocumentRepository supportingDocumentRepository;
    private final ObjectMapper objectMapper;
    private final RiskDecisioningService riskDecisioningService;

    public LoanApplicationDraftService(
            LoanApplicationDraftRepository repo,
            SupportingDocumentRepository supportingDocumentRepository,
            ObjectMapper objectMapper,
            RiskDecisioningService riskDecisioningService
    ) {
        this.repo = repo;
        this.supportingDocumentRepository = supportingDocumentRepository;
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
                .setSubmittedAt(null)
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

        enforceNotSubmitted(draft);
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

        enforceNotSubmitted(draft);
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

        enforceNotSubmitted(draft);

        RiskDecisioningService.RiskDecision rd = riskDecisioningService.scoreAndDecide(draft.getData());

        draft.setRiskScore(rd.score());
        draft.setDecision(rd.decision().name());
        draft.setDecisionReason(rd.reason());
        draft.setDecisionedAt(Instant.now());

        // Optionally also update status to reflect completion of automated decisioning.
        if (draft.getStatus() == null || "DRAFT".equalsIgnoreCase(draft.getStatus())) {
            draft.setStatus("DECISIONED");
        }

        draft.setUpdatedAt(Instant.now());
        return toResponse(repo.save(draft));
    }

    // PUBLIC_INTERFACE
    @Transactional
    public LoanDraftDtos.ReadinessResponse readiness(
            UUID userId,
            UUID draftId,
            List<String> requiredSections,
            List<String> requiredDocumentTypes
    ) {
        /**
         * Evaluate whether a draft is ready to be submitted:
         * - requiredSections must be present in draft.sectionStatus and marked complete
         * - requiredDocumentTypes must be present among docs linked to this draft where metadata.docType matches
         */
        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        List<String> missingSections = computeMissingRequiredSections(draft, requiredSections);
        List<String> missingDocTypes = computeMissingRequiredDocumentTypes(userId, draftId, requiredDocumentTypes);

        boolean ready = missingSections.isEmpty() && missingDocTypes.isEmpty();
        return new LoanDraftDtos.ReadinessResponse(draftId, ready, missingSections, missingDocTypes);
    }

    // PUBLIC_INTERFACE
    @Transactional
    public LoanDraftDtos.DraftResponse submit(UUID userId, UUID draftId, LoanDraftDtos.SubmitRequest req) {
        /**
         * Submit a draft:
         * - enforce readiness (required sections + docs)
         * - optionally run decisioning
         * - set submittedAt and lock further edits
         */
        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        enforceNotSubmitted(draft);

        List<String> missingSections = computeMissingRequiredSections(draft, req.requiredSections());
        List<String> missingDocTypes = computeMissingRequiredDocumentTypes(userId, draftId, req.requiredDocumentTypes());

        if (!missingSections.isEmpty() || !missingDocTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Draft is not ready to submit. Missing sections=" + missingSections + " missingDocumentTypes=" + missingDocTypes
            );
        }

        boolean shouldRunDecisioning = req.runDecisioning() == null || Boolean.TRUE.equals(req.runDecisioning());
        if (shouldRunDecisioning) {
            RiskDecisioningService.RiskDecision rd = riskDecisioningService.scoreAndDecide(draft.getData());
            draft.setRiskScore(rd.score());
            draft.setDecision(rd.decision().name());
            draft.setDecisionReason(rd.reason());
            draft.setDecisionedAt(Instant.now());
        }

        draft.setStatus("SUBMITTED");
        draft.setSubmittedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());

        return toResponse(repo.save(draft));
    }

    // PUBLIC_INTERFACE
    @Transactional
    public void delete(UUID userId, UUID draftId) {
        /** Delete a draft owned by the current user. */
        LoanApplicationDraft draft = repo.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        enforceNotSubmitted(draft);
        repo.delete(draft);
    }

    private void enforceNotSubmitted(LoanApplicationDraft draft) {
        if (draft.getSubmittedAt() != null || "SUBMITTED".equalsIgnoreCase(draft.getStatus())) {
            throw new IllegalArgumentException("Draft is submitted and locked. No further modifications are allowed.");
        }
    }

    private void enforceExpectedVersionIfPresent(LoanApplicationDraft draft, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!expectedVersion.equals(draft.getVersion())) {
            throw new IllegalArgumentException("Version mismatch. Expected=" + expectedVersion + " actual=" + draft.getVersion());
        }
    }

    private List<String> computeMissingRequiredSections(LoanApplicationDraft draft, List<String> requiredSections) {
        if (requiredSections == null) {
            return List.of();
        }

        ObjectNode sectionStatus = parseObjectOrThrow(draft.getSectionStatus(), "stored sectionStatus");
        List<String> missing = new ArrayList<>();

        for (String sectionKey : requiredSections) {
            if (sectionKey == null || sectionKey.isBlank()) {
                continue;
            }

            JsonNode node = sectionStatus.get(sectionKey);
            if (node == null || node.isNull()) {
                missing.add(sectionKey);
                continue;
            }

            // Support both:
            // 1) "COMPLETED"
            // 2) {"state":"COMPLETED", ...}
            String state = null;
            if (node.isTextual()) {
                state = node.asText();
            } else if (node.isObject() && node.get("state") != null && node.get("state").isTextual()) {
                state = node.get("state").asText();
            }

            if (state == null || !"COMPLETED".equalsIgnoreCase(state)) {
                missing.add(sectionKey);
            }
        }
        return missing;
    }

    private List<String> computeMissingRequiredDocumentTypes(UUID userId, UUID draftId, List<String> requiredDocTypes) {
        if (requiredDocTypes == null) {
            return List.of();
        }

        List<SupportingDocument> docs = supportingDocumentRepository
                .findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, draftId);

        Set<String> presentTypes = docs.stream()
                .map(SupportingDocument::getMetadata)
                .map(this::extractDocTypeFromMetadata)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (String required : requiredDocTypes) {
            if (required == null || required.isBlank()) {
                continue;
            }
            if (!presentTypes.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    private String extractDocTypeFromMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (node == null || !node.isObject()) {
                return null;
            }
            JsonNode docType = node.get("docType");
            return (docType != null && docType.isTextual()) ? docType.asText() : null;
        } catch (JsonProcessingException e) {
            // If bad metadata exists historically, ignore it for readiness (treat as not present).
            return null;
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
                d.getSubmittedAt(),
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

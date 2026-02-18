package com.example.BusinessLoanAPISpringBoot.officer.service;

import com.example.BusinessLoanAPISpringBoot.audit.service.AuditEventService;
import com.example.BusinessLoanAPISpringBoot.loan.model.LoanApplicationDraft;
import com.example.BusinessLoanAPISpringBoot.loan.repo.LoanApplicationDraftRepository;
import com.example.BusinessLoanAPISpringBoot.officer.api.dto.OfficerDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Officer workflow business logic:
 * - Queue (submitted applications)
 * - Detail view
 * - Decision override
 */
@Service
public class OfficerService {

    private final LoanApplicationDraftRepository draftRepository;
    private final AuditEventService auditEventService;

    public OfficerService(LoanApplicationDraftRepository draftRepository, AuditEventService auditEventService) {
        this.draftRepository = draftRepository;
        this.auditEventService = auditEventService;
    }

    // PUBLIC_INTERFACE
    @Transactional(readOnly = true)
    public List<OfficerDtos.QueueItem> queue(String status, String decision) {
        /** List submitted applications for officer queue, optionally filtered by status/decision. */
        String statusFilter = (status == null || status.isBlank()) ? null : status.trim();
        String decisionFilter = (decision == null || decision.isBlank()) ? null : decision.trim();

        // Simple approach for MVP: use in-memory filtering on the most relevant set.
        // We intentionally keep repository surface small; can be optimized with derived queries later.
        List<LoanApplicationDraft> all = draftRepository.findAll().stream()
                .filter(d -> d.getSubmittedAt() != null || "SUBMITTED".equalsIgnoreCase(d.getStatus()))
                .sorted((a, b) -> {
                    Instant at = a.getSubmittedAt() == null ? Instant.EPOCH : a.getSubmittedAt();
                    Instant bt = b.getSubmittedAt() == null ? Instant.EPOCH : b.getSubmittedAt();
                    // newest first
                    return bt.compareTo(at);
                })
                .toList();

        return all.stream()
                .filter(d -> statusFilter == null || (d.getStatus() != null && d.getStatus().equalsIgnoreCase(statusFilter)))
                .filter(d -> decisionFilter == null || (d.getDecision() != null && d.getDecision().equalsIgnoreCase(decisionFilter)))
                .map(this::toQueueItem)
                .toList();
    }

    // PUBLIC_INTERFACE
    @Transactional(readOnly = true)
    public OfficerDtos.ApplicationDetail getApplicationDetail(UUID draftId) {
        /** Get full application detail for officer review. */
        LoanApplicationDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        return toDetail(draft);
    }

    // PUBLIC_INTERFACE
    @Transactional
    public OfficerDtos.DecisionOverrideResponse overrideDecision(
            UUID draftId,
            UUID officerUserId,
            String newDecision,
            String officerReason,
            String correlationId,
            String ipAddress,
            String userAgent
    ) {
        /** Override decision fields on a submitted application and write an audit event. */
        if (newDecision == null || newDecision.isBlank()) {
            throw new IllegalArgumentException("decision is required");
        }
        if (officerReason == null || officerReason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }

        LoanApplicationDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (draft.getSubmittedAt() == null && !"SUBMITTED".equalsIgnoreCase(draft.getStatus())) {
            throw new IllegalArgumentException("Only submitted applications can be overridden");
        }

        String previousDecision = draft.getDecision();
        Instant now = Instant.now();

        // Apply override by updating decision and reason; keep riskScore as-is for traceability.
        draft.setDecision(newDecision.trim().toUpperCase());
        draft.setDecisionReason("OVERRIDDEN: " + officerReason.trim());
        draft.setDecisionedAt(now);
        draft.setUpdatedAt(now);

        draftRepository.save(draft);

        auditEventService.writeEvent(
                "OFFICER_DECISION_OVERRIDE",
                officerUserId,
                correlationId,
                ipAddress,
                userAgent,
                "SUCCESS",
                Map.of(
                        "draftId", draftId.toString(),
                        "applicantUserId", draft.getUserId() == null ? null : draft.getUserId().toString(),
                        "previousDecision", previousDecision,
                        "newDecision", draft.getDecision(),
                        "reason", officerReason
                )
        );

        return new OfficerDtos.DecisionOverrideResponse(
                draftId,
                previousDecision,
                draft.getDecision(),
                officerReason,
                now
        );
    }

    private OfficerDtos.QueueItem toQueueItem(LoanApplicationDraft d) {
        return new OfficerDtos.QueueItem(
                d.getId(),
                d.getUserId(),
                d.getStatus(),
                d.getRiskScore(),
                d.getDecision(),
                d.getSubmittedAt(),
                d.getUpdatedAt()
        );
    }

    private OfficerDtos.ApplicationDetail toDetail(LoanApplicationDraft d) {
        return new OfficerDtos.ApplicationDetail(
                d.getId(),
                d.getUserId(),
                d.getData(),
                d.getSectionStatus(),
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
}

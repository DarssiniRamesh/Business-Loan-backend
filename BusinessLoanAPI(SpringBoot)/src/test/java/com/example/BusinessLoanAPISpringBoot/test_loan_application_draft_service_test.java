package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import com.example.BusinessLoanAPISpringBoot.auth.repo.AppUserRepository;
import com.example.BusinessLoanAPISpringBoot.documents.model.SupportingDocument;
import com.example.BusinessLoanAPISpringBoot.documents.repo.SupportingDocumentRepository;
import com.example.BusinessLoanAPISpringBoot.loan.api.dto.LoanDraftDtos;
import com.example.BusinessLoanAPISpringBoot.loan.model.LoanApplicationDraft;
import com.example.BusinessLoanAPISpringBoot.loan.repo.LoanApplicationDraftRepository;
import com.example.BusinessLoanAPISpringBoot.loan.service.LoanApplicationDraftService;
import com.example.BusinessLoanAPISpringBoot.loan.service.RiskDecisioningService;
import com.example.BusinessLoanAPISpringBoot.notifications.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Maps to documented test cases (implemented backend behavior):
 * - "Checklist renders all required FR-02 and FR-03 items with status" => backend readiness computes missing sections/doc types.
 * - "Submission gating disables Submit until all items Complete" => submit() rejects when missing.
 * - "Submit transitions status to Submitted ..." => submit() sets status/submittedAt and (optionally) decisioning.
 *
 * Note: Real-time UI update timing is a frontend/system concern and not testable here.
 */
class test_loan_application_draft_service_test {

    private static LoanApplicationDraftService newService(
            LoanApplicationDraftRepository repo,
            SupportingDocumentRepository docsRepo,
            RiskDecisioningService riskDecisioningService,
            AppUserRepository appUserRepository,
            NotificationService notificationService
    ) {
        return new LoanApplicationDraftService(
                repo,
                docsRepo,
                new ObjectMapper(),
                riskDecisioningService,
                appUserRepository,
                notificationService
        );
    }

    private static LoanApplicationDraft draft(UUID draftId, UUID userId, String sectionStatusJson) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LoanApplicationDraft()
                .setId(draftId)
                .setUserId(userId)
                .setData("{}")
                .setSectionStatus(sectionStatusJson)
                .setCurrentStep("businessInfo")
                .setStatus("DRAFT")
                .setSubmittedAt(null)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(1L);
    }

    private static SupportingDocument doc(UUID userId, UUID draftId, String docType) {
        return new SupportingDocument()
                .setId(UUID.randomUUID())
                .setUserId(userId)
                .setLoanDraftId(draftId)
                .setOriginalFilename("x.pdf")
                .setContentType("application/pdf")
                .setSizeBytes(1L)
                .setSha256Hex("a".repeat(64))
                .setStorageKey("k")
                .setMetadata("{\"docType\":\"" + docType + "\"}")
                .setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("readiness returns ready=true when required sections completed and required docs present")
    void readiness_readyTrue_whenComplete() {
        LoanApplicationDraftRepository repo = mock(LoanApplicationDraftRepository.class);
        SupportingDocumentRepository docsRepo = mock(SupportingDocumentRepository.class);
        RiskDecisioningService riskDecisioningService = mock(RiskDecisioningService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        LoanApplicationDraftService svc = newService(repo, docsRepo, riskDecisioningService, appUserRepository, notificationService);

        UUID userId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        // Mix of supported formats: "COMPLETED" and {"state":"COMPLETED"}
        LoanApplicationDraft d = draft(draftId, userId,
                "{\"businessInfo\":\"COMPLETED\",\"ownerInfo\":{\"state\":\"COMPLETED\"},\"loanRequest\":\"COMPLETED\"}");

        when(repo.findByIdAndUserId(draftId, userId)).thenReturn(Optional.of(d));
        when(docsRepo.findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, draftId))
                .thenReturn(List.of(
                        doc(userId, draftId, "BANK_STATEMENT"),
                        doc(userId, draftId, "TAX_RETURN")
                ));

        LoanDraftDtos.ReadinessResponse resp = svc.readiness(
                userId,
                draftId,
                List.of("businessInfo", "ownerInfo", "loanRequest"),
                List.of("BANK_STATEMENT", "TAX_RETURN")
        );

        assertTrue(resp.ready());
        assertEquals(List.of(), resp.missingSections());
        assertEquals(List.of(), resp.missingDocumentTypes());
    }

    @Test
    @DisplayName("readiness returns missing sections/doc types when incomplete")
    void readiness_readyFalse_whenMissing() {
        LoanApplicationDraftRepository repo = mock(LoanApplicationDraftRepository.class);
        SupportingDocumentRepository docsRepo = mock(SupportingDocumentRepository.class);
        RiskDecisioningService riskDecisioningService = mock(RiskDecisioningService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        LoanApplicationDraftService svc = newService(repo, docsRepo, riskDecisioningService, appUserRepository, notificationService);

        UUID userId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        LoanApplicationDraft d = draft(draftId, userId, "{\"businessInfo\":\"COMPLETED\"}");

        when(repo.findByIdAndUserId(draftId, userId)).thenReturn(Optional.of(d));
        when(docsRepo.findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, draftId))
                .thenReturn(List.of(doc(userId, draftId, "BANK_STATEMENT")));

        LoanDraftDtos.ReadinessResponse resp = svc.readiness(
                userId,
                draftId,
                List.of("businessInfo", "ownerInfo"),
                List.of("BANK_STATEMENT", "TAX_RETURN")
        );

        assertFalse(resp.ready());
        assertEquals(List.of("ownerInfo"), resp.missingSections());
        assertEquals(List.of("TAX_RETURN"), resp.missingDocumentTypes());
    }

    @Test
    @DisplayName("submit rejects when draft is not ready (gating)")
    void submit_notReady_rejected() {
        LoanApplicationDraftRepository repo = mock(LoanApplicationDraftRepository.class);
        SupportingDocumentRepository docsRepo = mock(SupportingDocumentRepository.class);
        RiskDecisioningService riskDecisioningService = mock(RiskDecisioningService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        LoanApplicationDraftService svc = newService(repo, docsRepo, riskDecisioningService, appUserRepository, notificationService);

        UUID userId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        LoanApplicationDraft d = draft(draftId, userId, "{\"businessInfo\":\"IN_PROGRESS\"}");
        when(repo.findByIdAndUserId(draftId, userId)).thenReturn(Optional.of(d));
        when(docsRepo.findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, draftId))
                .thenReturn(List.of());

        LoanDraftDtos.SubmitRequest req = new LoanDraftDtos.SubmitRequest(
                List.of("businessInfo"),
                List.of("TAX_RETURN"),
                true
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.submit(userId, draftId, req));
        assertTrue(ex.getMessage().contains("Draft is not ready to submit"));
        verify(riskDecisioningService, never()).scoreAndDecide(anyString());
        verify(notificationService, never()).onApplicationSubmitted(any());
    }

    @Test
    @DisplayName("submit sets status SUBMITTED and runs decisioning by default")
    void submit_ready_setsSubmitted_runsDecisioning() {
        LoanApplicationDraftRepository repo = mock(LoanApplicationDraftRepository.class);
        SupportingDocumentRepository docsRepo = mock(SupportingDocumentRepository.class);
        RiskDecisioningService riskDecisioningService = mock(RiskDecisioningService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        LoanApplicationDraftService svc = newService(repo, docsRepo, riskDecisioningService, appUserRepository, notificationService);

        UUID userId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        LoanApplicationDraft d = draft(draftId, userId, "{\"businessInfo\":\"COMPLETED\"}");

        when(repo.findByIdAndUserId(draftId, userId)).thenReturn(Optional.of(d));
        when(docsRepo.findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, draftId))
                .thenReturn(List.of(doc(userId, draftId, "TAX_RETURN")));

        when(riskDecisioningService.scoreAndDecide(anyString()))
                .thenReturn(new RiskDecisioningService.RiskDecision(85, RiskDecisioningService.Decision.PRE_QUALIFIED, "ok"));

        when(repo.save(any(LoanApplicationDraft.class))).thenAnswer(inv -> inv.getArgument(0, LoanApplicationDraft.class));

        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("applicant@example.com");
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        LoanDraftDtos.SubmitRequest req = new LoanDraftDtos.SubmitRequest(
                List.of("businessInfo"),
                List.of("TAX_RETURN"),
                null // default = run decisioning
        );

        LoanDraftDtos.DraftResponse resp = svc.submit(userId, draftId, req);

        assertEquals("SUBMITTED", resp.status());
        assertNotNull(resp.submittedAt());
        assertEquals("PRE_QUALIFIED", resp.decision());
        assertNotNull(resp.decisionedAt());

        verify(riskDecisioningService, times(1)).scoreAndDecide(anyString());
        verify(notificationService, times(1)).onApplicationSubmitted(any());

        // Ensure the saved draft is locked (submittedAt set)
        ArgumentCaptor<LoanApplicationDraft> savedCaptor = ArgumentCaptor.forClass(LoanApplicationDraft.class);
        verify(repo, times(1)).save(savedCaptor.capture());
        assertNotNull(savedCaptor.getValue().getSubmittedAt());
    }

    @Test
    @DisplayName("submit can skip decisioning when runDecisioning=false")
    void submit_canSkipDecisioning() {
        LoanApplicationDraftRepository repo = mock(LoanApplicationDraftRepository.class);
        SupportingDocumentRepository docsRepo = mock(SupportingDocumentRepository.class);
        RiskDecisioningService riskDecisioningService = mock(RiskDecisioningService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        LoanApplicationDraftService svc = newService(repo, docsRepo, riskDecisioningService, appUserRepository, notificationService);

        UUID userId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        LoanApplicationDraft d = draft(draftId, userId, "{\"businessInfo\":\"COMPLETED\"}");

        when(repo.findByIdAndUserId(draftId, userId)).thenReturn(Optional.of(d));
        when(docsRepo.findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, draftId))
                .thenReturn(List.of(doc(userId, draftId, "TAX_RETURN")));

        when(repo.save(any(LoanApplicationDraft.class))).thenAnswer(inv -> inv.getArgument(0, LoanApplicationDraft.class));
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        LoanDraftDtos.SubmitRequest req = new LoanDraftDtos.SubmitRequest(
                List.of("businessInfo"),
                List.of("TAX_RETURN"),
                false
        );

        LoanDraftDtos.DraftResponse resp = svc.submit(userId, draftId, req);

        assertEquals("SUBMITTED", resp.status());
        verify(riskDecisioningService, never()).scoreAndDecide(anyString());
    }
}

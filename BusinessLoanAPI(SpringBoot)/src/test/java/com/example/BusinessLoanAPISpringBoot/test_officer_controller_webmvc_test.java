package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.audit.model.AuditEvent;
import com.example.BusinessLoanAPISpringBoot.audit.service.AuditEventService;
import com.example.BusinessLoanAPISpringBoot.auth.security.SecurityConfig;
import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import com.example.BusinessLoanAPISpringBoot.officer.api.OfficerController;
import com.example.BusinessLoanAPISpringBoot.officer.api.dto.OfficerDtos;
import com.example.BusinessLoanAPISpringBoot.officer.service.OfficerService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Maps to documented test cases (implemented endpoints are under /api/officer):
 * - "API denies unauthenticated access to /api/internal/* with 401" => here: /api/officer/* returns 401 without JWT.
 * - "Forbidden (403) for authenticated user without LOAN_OFFICER or ADMIN"
 * - "Authorized LOAN_OFFICER can access queue and detail APIs (200)"
 * - "Audit query endpoint returns events sorted by timestamp with filters"
 */
@WebMvcTest(controllers = OfficerController.class)
@Import(SecurityConfig.class)
class test_officer_controller_webmvc_test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private OfficerService officerService;

    @MockBean
    private AuditEventService auditEventService;

    private void stubJwt(String token, UUID subjectUserId, List<String> roles) {
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(subjectUserId.toString());
        when(claims.get("roles", List.class)).thenReturn(roles);
        when(jwtService.parseAndValidate(token)).thenReturn(claims);
    }

    @Test
    @DisplayName("GET /api/officer/queue without JWT returns 401")
    void queue_withoutJwt_is401() throws Exception {
        mockMvc.perform(get("/api/officer/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/officer/queue with APPLICANT role returns 403")
    void queue_withApplicantRole_is403() throws Exception {
        String token = "t1";
        stubJwt(token, UUID.randomUUID(), List.of("APPLICANT"));

        mockMvc.perform(get("/api/officer/queue").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/officer/queue with LOAN_OFFICER role returns 200 and queue list")
    void queue_withOfficerRole_is200() throws Exception {
        String token = "t2";
        stubJwt(token, UUID.randomUUID(), List.of("LOAN_OFFICER"));

        OfficerDtos.QueueItem row = new OfficerDtos.QueueItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SUBMITTED",
                72,
                "MANUAL_REVIEW",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z")
        );
        when(officerService.queue(any(), any())).thenReturn(List.of(row));

        mockMvc.perform(get("/api/officer/queue").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("GET /api/officer/audit enforces sort by createdAt desc and caps size to <=200")
    void auditQuery_sortsDesc_capsSize() throws Exception {
        // Documented case: "Audit query endpoint returns events sorted by timestamp with filters"
        String token = "t3";
        stubJwt(token, UUID.randomUUID(), List.of("ADMIN"));

        AuditEvent e1 = new AuditEvent()
                .setId(UUID.randomUUID())
                .setEventType("OFFICER_DECISION_OVERRIDE")
                .setUserId(UUID.randomUUID())
                .setOutcome("SUCCESS")
                .setDetails("{\"k\":\"v\"}")
                .setCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        Page<AuditEvent> page = new PageImpl<>(List.of(e1), PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")), 1);

        when(auditEventService.query(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/officer/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        // size attempts to exceed cap
                        .param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].eventType").value("OFFICER_DECISION_OVERRIDE"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventService, times(1))
                .query(any(), any(), any(), any(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        // size must be capped at 200
        org.junit.jupiter.api.Assertions.assertEquals(200, pageable.getPageSize());
        // sort must be createdAt desc
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        org.junit.jupiter.api.Assertions.assertNotNull(order);
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("GET /api/officer/audit with invalid from timestamp returns 400 with structured error")
    void auditQuery_invalidTimestamp_is400() throws Exception {
        String token = "t4";
        stubJwt(token, UUID.randomUUID(), List.of("LOAN_OFFICER"));

        mockMvc.perform(get("/api/officer/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", "not-a-timestamp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("from must be an ISO-8601 instant")));
    }

    @Test
    @DisplayName("POST /api/officer/applications/{draftId}/override-decision passes correlation id and request metadata")
    void overrideDecision_passesCorrelationId() throws Exception {
        String token = "t5";
        UUID officerUserId = UUID.randomUUID();
        stubJwt(token, officerUserId, List.of("LOAN_OFFICER"));

        UUID draftId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-03T00:00:00Z");

        when(officerService.overrideDecision(
                eq(draftId),
                eq(officerUserId),
                eq("MANUAL_REVIEW"),
                eq("needs more docs"),
                eq("corr-123"),
                anyString(),
                eq("JUnit")
        )).thenReturn(new OfficerDtos.DecisionOverrideResponse(draftId, "DECLINED", "MANUAL_REVIEW", "needs more docs", now));

        mockMvc.perform(post("/api/officer/applications/{draftId}/override-decision", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Correlation-Id", "corr-123")
                        .header("User-Agent", "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MANUAL_REVIEW\",\"reason\":\"needs more docs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(draftId.toString()))
                .andExpect(jsonPath("$.newDecision").value("MANUAL_REVIEW"));

        verify(officerService, times(1)).overrideDecision(
                eq(draftId),
                eq(officerUserId),
                eq("MANUAL_REVIEW"),
                eq("needs more docs"),
                eq("corr-123"),
                anyString(),
                eq("JUnit")
        );
    }
}

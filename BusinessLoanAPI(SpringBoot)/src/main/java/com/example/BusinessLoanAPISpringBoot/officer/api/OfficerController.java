package com.example.BusinessLoanAPISpringBoot.officer.api;

import com.example.BusinessLoanAPISpringBoot.audit.model.AuditEvent;
import com.example.BusinessLoanAPISpringBoot.audit.service.AuditEventService;
import com.example.BusinessLoanAPISpringBoot.officer.api.dto.OfficerDtos;
import com.example.BusinessLoanAPISpringBoot.officer.service.OfficerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Officer-facing APIs.
 *
 * Security:
 * - Requires JWT Bearer token
 * - Must have role LOAN_OFFICER or ADMIN (ROLE_LOAN_OFFICER / ROLE_ADMIN)
 */
@RestController
@RequestMapping("/api/officer")
@Tag(name = "Officer", description = "Officer queue, detail review, decision overrides, and audit log queries")
public class OfficerController {

    private final OfficerService officerService;
    private final AuditEventService auditEventService;

    public OfficerController(OfficerService officerService, AuditEventService auditEventService) {
        this.officerService = officerService;
        this.auditEventService = auditEventService;
    }

    // PUBLIC_INTERFACE
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    @Operation(
            summary = "Officer queue",
            description = "Lists submitted applications for officer review. Optional filters: status, decision."
    )
    public List<OfficerDtos.QueueItem> queue(
            @Parameter(description = "Optional status filter (e.g. SUBMITTED).") @RequestParam(required = false) String status,
            @Parameter(description = "Optional decision filter (e.g. MANUAL_REVIEW).") @RequestParam(required = false) String decision
    ) {
        return officerService.queue(status, decision);
    }

    // PUBLIC_INTERFACE
    @GetMapping("/applications/{draftId}")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    @Operation(
            summary = "Application detail",
            description = "Returns full application detail (stored draft JSON + decisioning fields) for officer review."
    )
    public OfficerDtos.ApplicationDetail applicationDetail(@PathVariable UUID draftId) {
        return officerService.getApplicationDetail(draftId);
    }

    // PUBLIC_INTERFACE
    @PostMapping("/applications/{draftId}/override-decision")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    @Operation(
            summary = "Override decision",
            description = "Overrides an application's decision and writes an immutable audit event."
    )
    public OfficerDtos.DecisionOverrideResponse overrideDecision(
            Authentication auth,
            HttpServletRequest request,
            @PathVariable UUID draftId,
            @Valid @RequestBody OfficerDtos.DecisionOverrideRequest req
    ) {
        UUID officerUserId = subjectAsUserId(auth);

        // Correlation id: support inbound X-Correlation-Id header (optional)
        String correlationId = request.getHeader("X-Correlation-Id");
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        return officerService.overrideDecision(
                draftId,
                officerUserId,
                req.decision(),
                req.reason(),
                correlationId,
                ipAddress,
                userAgent
        );
    }

    // PUBLIC_INTERFACE
    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    @Operation(
            summary = "Query audit log",
            description = "Queries audit events by time range with optional eventType/userId filters. Supports paging via page/size."
    )
    public Page<OfficerDtos.AuditEventResponse> auditLog(
            @Parameter(description = "ISO-8601 start timestamp (inclusive). Default: epoch.")
            @RequestParam(required = false) String from,
            @Parameter(description = "ISO-8601 end timestamp (inclusive). Default: now.")
            @RequestParam(required = false) String to,
            @Parameter(description = "Optional event type filter (e.g. OFFICER_DECISION_OVERRIDE).")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "Optional user id (UUID) filter (actor user id stored on the event).")
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Instant fromInstant = parseInstantOrNull(from, "from");
        Instant toInstant = parseInstantOrNull(to, "to");

        // newest first
        PageRequest pr = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<AuditEvent> result = auditEventService.query(fromInstant, toInstant, eventType, userId, pr);
        return result.map(this::toAuditResponse);
    }

    private OfficerDtos.AuditEventResponse toAuditResponse(AuditEvent e) {
        return new OfficerDtos.AuditEventResponse(
                e.getId(),
                e.getEventType(),
                e.getUserId(),
                e.getCorrelationId(),
                e.getIpAddress(),
                e.getUserAgent(),
                e.getOutcome(),
                e.getDetails(),
                e.getCreatedAt()
        );
    }

    private Instant parseInstantOrNull(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 instant, e.g. 2026-01-01T12:34:56Z");
        }
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

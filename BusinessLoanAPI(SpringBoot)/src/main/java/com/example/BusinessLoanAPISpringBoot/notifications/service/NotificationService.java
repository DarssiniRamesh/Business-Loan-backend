package com.example.BusinessLoanAPISpringBoot.notifications.service;

import com.example.BusinessLoanAPISpringBoot.notifications.config.NotificationProperties;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationEventType;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.EmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Orchestrates notifications for key business events (submission and decisions).
 *
 * This is intentionally best-effort:
 * - If notifications are disabled, no-ops.
 * - If contact info is missing, no-ops for that channel.
 * - If a provider fails, we log and do not fail the primary business workflow.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationProperties props;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;

    public NotificationService(NotificationProperties props, EmailProvider emailProvider, SmsProvider smsProvider) {
        this.props = props;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
    }

    // PUBLIC_INTERFACE
    public void onApplicationSubmitted(NotificationRequest req) {
        /** Trigger notifications for application submission. */
        sendBestEffort(req, "Application submitted", buildSubmissionBody(req));
    }

    // PUBLIC_INTERFACE
    public void onDecisionProduced(NotificationRequest req) {
        /** Trigger notifications when an automated decision is produced. */
        String decision = safe(req.decision());
        sendBestEffort(req, "Decision update: " + decision, buildDecisionBody(req, false));
    }

    // PUBLIC_INTERFACE
    public void onDecisionOverridden(NotificationRequest req) {
        /** Trigger notifications when an officer overrides a decision. */
        String decision = safe(req.decision());
        sendBestEffort(req, "Decision updated by loan officer: " + decision, buildDecisionBody(req, true));
    }

    private void sendBestEffort(NotificationRequest req, String subject, String body) {
        if (!props.isEnabled()) {
            log.debug("Notifications disabled; skipping eventType={} draftId={}", req.eventType(), req.draftId());
            return;
        }

        // Applicant email
        if (req.applicantEmail() != null && !req.applicantEmail().isBlank()) {
            try {
                emailProvider.send(req, req.applicantEmail().trim(), subject, body);
            } catch (Exception e) {
                log.warn("Email notification failed (ignored). eventType={} draftId={} error={}",
                        req.eventType(), req.draftId(), e.toString());
            }
        }

        // Applicant SMS (not wired in MVP because phone isn't stored); kept for future.
        if (req.applicantPhone() != null && !req.applicantPhone().isBlank()) {
            try {
                smsProvider.send(req, req.applicantPhone().trim(), body);
            } catch (Exception e) {
                log.warn("SMS notification failed (ignored). eventType={} draftId={} error={}",
                        req.eventType(), req.draftId(), e.toString());
            }
        }

        // Optional officer copy (email)
        if (props.getOfficerEmail() != null && !props.getOfficerEmail().isBlank()) {
            try {
                emailProvider.send(req, props.getOfficerEmail().trim(), "[Officer Copy] " + subject, body);
            } catch (Exception e) {
                log.warn("Officer email notification failed (ignored). eventType={} draftId={} error={}",
                        req.eventType(), req.draftId(), e.toString());
            }
        }

        // Optional officer copy (sms)
        if (props.getOfficerPhone() != null && !props.getOfficerPhone().isBlank()) {
            try {
                smsProvider.send(req, props.getOfficerPhone().trim(), "[Officer Copy] " + body);
            } catch (Exception e) {
                log.warn("Officer SMS notification failed (ignored). eventType={} draftId={} error={}",
                        req.eventType(), req.draftId(), e.toString());
            }
        }
    }

    private String buildSubmissionBody(NotificationRequest req) {
        return "Your business loan application has been submitted.\n\n"
                + "Application ID: " + req.draftId() + "\n"
                + "Submitted at: " + req.occurredAt() + "\n";
    }

    private String buildDecisionBody(NotificationRequest req, boolean overridden) {
        String header = overridden
                ? "A loan officer updated your application decision."
                : "A decision has been produced for your application.";

        String reason = safe(req.decisionReason());
        if (!reason.isBlank()) {
            // Keep message compact for SMS; email stub will still show it.
            reason = truncate(reason, 400);
        }

        return header + "\n\n"
                + "Application ID: " + req.draftId() + "\n"
                + "Decision: " + safe(req.decision()) + "\n"
                + (reason.isBlank() ? "" : ("Reason: " + reason + "\n"))
                + "Updated at: " + req.occurredAt() + "\n";
    }

    private String safe(String s) {
        return Objects.requireNonNullElse(s, "").trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }
}

package com.example.BusinessLoanAPISpringBoot.notifications.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Provider-agnostic notification request.
 *
 * @param eventType which business event triggered the notification
 * @param draftId loan draft/application id
 * @param applicantUserId applicant user id
 * @param applicantEmail applicant email (optional)
 * @param applicantPhone applicant phone (optional; not currently stored in MVP auth schema)
 * @param decision decision string if present
 * @param decisionReason decision reason if present
 * @param occurredAt timestamp of the event
 */
public record NotificationRequest(
        NotificationEventType eventType,
        UUID draftId,
        UUID applicantUserId,
        String applicantEmail,
        String applicantPhone,
        String decision,
        String decisionReason,
        Instant occurredAt
) {
}

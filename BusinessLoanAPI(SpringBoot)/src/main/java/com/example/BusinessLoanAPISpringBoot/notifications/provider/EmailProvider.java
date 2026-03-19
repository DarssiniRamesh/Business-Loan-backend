package com.example.BusinessLoanAPISpringBoot.notifications.provider;

import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;

/**
 * Provider interface for sending emails.
 */
public interface EmailProvider {

    /**
     * Send an email notification for the given request.
     *
     * Implementations should be best-effort and throw only for unrecoverable configuration errors.
     */
    void send(NotificationRequest request, String toEmail, String subject, String bodyText);
}

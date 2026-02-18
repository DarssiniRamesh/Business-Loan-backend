package com.example.BusinessLoanAPISpringBoot.notifications.model;

/**
 * High-level event types that can trigger notifications.
 */
public enum NotificationEventType {
    APPLICATION_SUBMITTED,
    DECISION_PRODUCED,
    DECISION_OVERRIDDEN,

    /**
     * MFA one-time-password delivery (email/SMS).
     */
    MFA_OTP
}

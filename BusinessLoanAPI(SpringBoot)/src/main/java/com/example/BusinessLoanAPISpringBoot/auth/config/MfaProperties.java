package com.example.BusinessLoanAPISpringBoot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MFA configuration properties.
 *
 * These settings control how OTPs are delivered and whether a development fallback is enabled.
 *
 * Loaded from application.properties (and env var overrides) under prefix "app.mfa".
 */
@ConfigurationProperties(prefix = "app.mfa")
public class MfaProperties {

    /**
     * If true, the system will attempt to deliver OTPs via the configured notification providers (email/SMS).
     *
     * Note: Even when enabled, delivery can still fail if provider credentials are missing.
     */
    private boolean deliveryEnabled = true;

    /**
     * If true, /api/auth/login will include a "devOtp" field when MFA is pending.
     *
     * This is intended ONLY for dev/test environments when real email/SMS delivery is not configured.
     * Set to false in production.
     */
    private boolean devReturnOtp = true;

    /**
     * If true, OTP will be logged in plaintext as a last-resort fallback when delivery fails.
     *
     * WARNING: This is insecure; keep disabled in production.
     */
    private boolean logOtpOnFailure = false;

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public void setDeliveryEnabled(boolean deliveryEnabled) {
        this.deliveryEnabled = deliveryEnabled;
    }

    public boolean isDevReturnOtp() {
        return devReturnOtp;
    }

    public void setDevReturnOtp(boolean devReturnOtp) {
        this.devReturnOtp = devReturnOtp;
    }

    public boolean isLogOtpOnFailure() {
        return logOtpOnFailure;
    }

    public void setLogOtpOnFailure(boolean logOtpOnFailure) {
        this.logOtpOnFailure = logOtpOnFailure;
    }
}

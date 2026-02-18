package com.example.BusinessLoanAPISpringBoot.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for notifications.
 *
 * Populated from application.properties (and env var overrides).
 */
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    private boolean enabled = false;

    private final ChannelProperties email = new ChannelProperties();
    private final ChannelProperties sms = new ChannelProperties();

    private String officerEmail;
    private String officerPhone;

    private final SendGridProperties sendgrid = new SendGridProperties();
    private final TwilioProperties twilio = new TwilioProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ChannelProperties getEmail() {
        return email;
    }

    public ChannelProperties getSms() {
        return sms;
    }

    public String getOfficerEmail() {
        return officerEmail;
    }

    public void setOfficerEmail(String officerEmail) {
        this.officerEmail = officerEmail;
    }

    public String getOfficerPhone() {
        return officerPhone;
    }

    public void setOfficerPhone(String officerPhone) {
        this.officerPhone = officerPhone;
    }

    public SendGridProperties getSendgrid() {
        return sendgrid;
    }

    public TwilioProperties getTwilio() {
        return twilio;
    }

    public static class ChannelProperties {
        /**
         * Provider key: stub | sendgrid (email) | twilio (sms) | ...
         */
        private String provider = "stub";

        /**
         * From identity (email address or E.164 phone).
         */
        private String from;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    public static class SendGridProperties {
        private String apiKey;
        private String baseUrl = "https://api.sendgrid.com";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class TwilioProperties {
        private String accountSid;
        private String authToken;
        private String baseUrl = "https://api.twilio.com";

        public String getAccountSid() {
            return accountSid;
        }

        public void setAccountSid(String accountSid) {
            this.accountSid = accountSid;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}

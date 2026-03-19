package com.example.BusinessLoanAPISpringBoot.notifications.config;

import com.example.BusinessLoanAPISpringBoot.notifications.provider.EmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.SmsProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.mailjet.MailjetEmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.sendgrid.SendGridEmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.stub.StubEmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.stub.StubSmsProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.twilio.TwilioSmsProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Notification wiring.
 *
 * Notes:
 * - We intentionally default to stub providers and disabled notifications so the app runs without credentials.
 * - Real provider beans (SendGrid/Twilio/Mailjet) can be selected via app.notifications.*.provider.
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    public EmailProvider emailProvider(NotificationProperties props, ObjectMapper objectMapper) {
        String providerRaw = props.getEmail().getProvider();
        String provider = providerRaw == null ? "" : providerRaw.trim();

        // Important: many environments end up leaving provider as "stub" while setting real credentials.
        // Auto-detect a real provider when credentials are present so OTP emails are actually delivered.
        if (provider.isBlank() || "stub".equalsIgnoreCase(provider)) {
            boolean hasMailjet = notBlank(props.getMailjet().getApiKey()) && notBlank(props.getMailjet().getApiSecret());
            if (hasMailjet) {
                log.info("Email provider is stub/blank but Mailjet credentials detected; using MailjetEmailProvider.");
                return new MailjetEmailProvider(props, objectMapper);
            }

            boolean hasSendgrid = notBlank(props.getSendgrid().getApiKey());
            if (hasSendgrid) {
                log.info("Email provider is stub/blank but SendGrid API key detected; using SendGridEmailProvider.");
                return new SendGridEmailProvider(props, objectMapper);
            }

            return new StubEmailProvider(props);
        }

        if ("sendgrid".equalsIgnoreCase(provider)) {
            return new SendGridEmailProvider(props, objectMapper);
        }
        if ("mailjet".equalsIgnoreCase(provider)) {
            return new MailjetEmailProvider(props, objectMapper);
        }
        throw new IllegalArgumentException("Unknown email provider: " + provider);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Bean
    public SmsProvider smsProvider(NotificationProperties props) {
        String provider = props.getSms().getProvider();
        if (provider == null || provider.isBlank() || "stub".equalsIgnoreCase(provider)) {
            return new StubSmsProvider(props);
        }
        if ("twilio".equalsIgnoreCase(provider)) {
            return new TwilioSmsProvider(props);
        }
        throw new IllegalArgumentException("Unknown SMS provider: " + provider);
    }

    @Bean
    public NotificationService notificationService(
            NotificationProperties props,
            EmailProvider emailProvider,
            SmsProvider smsProvider
    ) {
        return new NotificationService(props, emailProvider, smsProvider);
    }
}

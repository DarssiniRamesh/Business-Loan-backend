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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Notification wiring.
 *
 * Notes:
 * - We intentionally default to stub providers and disabled notifications so the app runs without credentials.
 * - Real provider beans (SendGrid/Twilio) can be added later and selected via app.notifications.*.provider.
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public EmailProvider emailProvider(NotificationProperties props, ObjectMapper objectMapper) {
        String provider = props.getEmail().getProvider();
        if (provider == null || provider.isBlank() || "stub".equalsIgnoreCase(provider)) {
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

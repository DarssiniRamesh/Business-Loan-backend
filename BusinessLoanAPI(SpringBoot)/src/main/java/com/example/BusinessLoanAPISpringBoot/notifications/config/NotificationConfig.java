package com.example.BusinessLoanAPISpringBoot.notifications.config;

import com.example.BusinessLoanAPISpringBoot.notifications.provider.EmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.SmsProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.stub.StubEmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.stub.StubSmsProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.service.NotificationService;
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
    public EmailProvider emailProvider(NotificationProperties props) {
        // Currently only stub is implemented; hooks exist for sendgrid provider later.
        return new StubEmailProvider(props);
    }

    @Bean
    public SmsProvider smsProvider(NotificationProperties props) {
        // Currently only stub is implemented; hooks exist for twilio provider later.
        return new StubSmsProvider(props);
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

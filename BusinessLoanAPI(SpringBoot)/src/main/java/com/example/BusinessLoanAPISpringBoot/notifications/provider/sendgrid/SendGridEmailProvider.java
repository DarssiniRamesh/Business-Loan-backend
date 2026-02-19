package com.example.BusinessLoanAPISpringBoot.notifications.provider.sendgrid;

import com.example.BusinessLoanAPISpringBoot.notifications.config.NotificationProperties;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.EmailProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * SendGrid implementation of {@link EmailProvider}.
 *
 * Uses SendGrid Web API v3 (/v3/mail/send). Requires SENDGRID_API_KEY to be configured.
 */
public class SendGridEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailProvider.class);

    private final NotificationProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SendGridEmailProvider(NotificationProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void send(NotificationRequest request, String toEmail, String subject, String bodyText) {
        String apiKey = props.getSendgrid().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SendGrid API key missing. Set SENDGRID_API_KEY / app.notifications.sendgrid.api-key.");
        }

        String fromEmail = props.getEmail().getFrom();
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Email 'from' missing. Set APP_NOTIFICATIONS_EMAIL_FROM / app.notifications.email.from.");
        }

        try {
            // Minimal SendGrid payload
            Map<String, Object> payload = Map.of(
                    "personalizations", new Object[]{
                            Map.of("to", new Object[]{Map.of("email", toEmail)})
                    },
                    "from", Map.of("email", fromEmail),
                    "subject", subject,
                    "content", new Object[]{
                            Map.of("type", "text/plain", "value", bodyText)
                    }
            );

            String json = objectMapper.writeValueAsString(payload);

            String baseUrl = props.getSendgrid().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.sendgrid.com";
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v3/mail/send"))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            // SendGrid returns 202 Accepted on success
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("SendGrid send failed status=" + resp.statusCode() + " body=" + resp.body());
            }

            log.debug("SendGrid email sent. to={} subject={} eventType={}", toEmail, subject, request == null ? null : request.eventType());
        } catch (Exception e) {
            throw new IllegalStateException("SendGrid send failed: " + e.getMessage(), e);
        }
    }
}

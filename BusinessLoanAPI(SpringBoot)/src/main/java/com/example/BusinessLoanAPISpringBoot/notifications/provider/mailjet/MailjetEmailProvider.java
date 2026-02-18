package com.example.BusinessLoanAPISpringBoot.notifications.provider.mailjet;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Mailjet implementation of {@link EmailProvider}.
 *
 * Uses Mailjet Send API v3.1 (/v3.1/send).
 *
 * Required configuration:
 * - app.notifications.mailjet.api-key   (env: MAILJET_API_KEY)
 * - app.notifications.mailjet.api-secret (env: MAILJET_API_SECRET)
 * - app.notifications.email.from        (env: APP_NOTIFICATIONS_EMAIL_FROM)
 */
public class MailjetEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(MailjetEmailProvider.class);

    private final NotificationProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MailjetEmailProvider(NotificationProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void send(NotificationRequest request, String toEmail, String subject, String bodyText) {
        String apiKey = props.getMailjet().getApiKey();
        String apiSecret = props.getMailjet().getApiSecret();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException(
                    "Mailjet credentials missing. Set MAILJET_API_KEY/MAILJET_API_SECRET or app.notifications.mailjet.api-key/api-secret."
            );
        }

        String fromEmail = props.getEmail().getFrom();
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Email 'from' missing. Set APP_NOTIFICATIONS_EMAIL_FROM / app.notifications.email.from.");
        }

        String baseUrl = props.getMailjet().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.mailjet.com";
        }

        try {
            // Minimal Mailjet v3.1 payload
            // See: https://dev.mailjet.com/email/guides/send-api-v31/
            Map<String, Object> message = Map.of(
                    "From", Map.of("Email", fromEmail),
                    "To", new Object[]{Map.of("Email", toEmail)},
                    "Subject", subject,
                    "TextPart", bodyText
            );

            Map<String, Object> payload = Map.of("Messages", new Object[]{message});
            String json = objectMapper.writeValueAsString(payload);

            String basicAuth = Base64.getEncoder().encodeToString(
                    (apiKey.trim() + ":" + apiSecret.trim()).getBytes(StandardCharsets.UTF_8)
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v3.1/send"))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Mailjet returns 200 OK on success; errors can also be 4xx/5xx with body details.
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("Mailjet send failed status=" + resp.statusCode() + " body=" + resp.body());
            }

            log.debug("Mailjet email sent. to={} subject={} eventType={}",
                    toEmail, subject, request == null ? null : request.eventType());
        } catch (Exception e) {
            throw new IllegalStateException("Mailjet send failed: " + e.getMessage(), e);
        }
    }
}

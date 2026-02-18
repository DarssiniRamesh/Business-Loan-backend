package com.example.BusinessLoanAPISpringBoot.notifications.provider.twilio;

import com.example.BusinessLoanAPISpringBoot.notifications.config.NotificationProperties;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Twilio implementation of {@link SmsProvider}.
 *
 * Uses Twilio REST API to send a message:
 * POST /2010-04-01/Accounts/{AccountSid}/Messages.json
 *
 * Requires:
 * - TWILIO_ACCOUNT_SID
 * - TWILIO_AUTH_TOKEN
 * - APP_NOTIFICATIONS_SMS_FROM (E.164)
 */
public class TwilioSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsProvider.class);

    private final NotificationProperties props;
    private final HttpClient httpClient;

    public TwilioSmsProvider(NotificationProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void send(NotificationRequest request, String toPhoneE164, String message) {
        String sid = props.getTwilio().getAccountSid();
        String token = props.getTwilio().getAuthToken();

        if (sid == null || sid.isBlank()) {
            throw new IllegalStateException("Twilio account SID missing. Set TWILIO_ACCOUNT_SID / app.notifications.twilio.account-sid.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Twilio auth token missing. Set TWILIO_AUTH_TOKEN / app.notifications.twilio.auth-token.");
        }

        String from = props.getSms().getFrom();
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("SMS 'from' missing. Set APP_NOTIFICATIONS_SMS_FROM / app.notifications.sms.from.");
        }

        try {
            String baseUrl = props.getTwilio().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.twilio.com";
            }

            String form = "From=" + url(from)
                    + "&To=" + url(toPhoneE164)
                    + "&Body=" + url(message);

            String basic = Base64.getEncoder().encodeToString((sid.trim() + ":" + token.trim()).getBytes(StandardCharsets.UTF_8));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/2010-04-01/Accounts/" + sid.trim() + "/Messages.json"))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("Twilio send failed status=" + resp.statusCode() + " body=" + resp.body());
            }

            log.debug("Twilio SMS sent. to={} eventType={}", toPhoneE164, request == null ? null : request.eventType());
        } catch (Exception e) {
            throw new IllegalStateException("Twilio send failed: " + e.getMessage(), e);
        }
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

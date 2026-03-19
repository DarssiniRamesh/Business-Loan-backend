package com.example.BusinessLoanAPISpringBoot.notifications.provider.mailjet;

import com.example.BusinessLoanAPISpringBoot.notifications.config.NotificationProperties;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationEventType;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailjetEmailProviderTest {

    private MailjetEmailProvider provider;
    private NotificationProperties props;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        props = new NotificationProperties();
        objectMapper = new ObjectMapper();
        httpClient = mock(HttpClient.class);
        provider = new MailjetEmailProvider(props, objectMapper, httpClient);
    }

    @Test
    void send_success() throws Exception {
        props.getMailjet().setApiKey("key");
        props.getMailjet().setApiSecret("secret");
        props.getEmail().setFrom("sender@example.com");

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        
        // Fix for generic type inference
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(response);

        NotificationRequest req = new NotificationRequest(
                NotificationEventType.MFA_OTP, null, UUID.randomUUID(), "recipient@example.com", null, null, null, Instant.now()
        );
        
        provider.send(req, "recipient@example.com", "Subject", "Body");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        
        HttpRequest request = captor.getValue();
        assertEquals("POST", request.method());
        assertEquals("https://api.mailjet.com/v3.1/send", request.uri().toString());
    }

    @Test
    void send_throwsOnMissingCredentials() {
        assertThrows(IllegalStateException.class, () -> 
            provider.send(null, "to", "sub", "body")
        );
    }
}

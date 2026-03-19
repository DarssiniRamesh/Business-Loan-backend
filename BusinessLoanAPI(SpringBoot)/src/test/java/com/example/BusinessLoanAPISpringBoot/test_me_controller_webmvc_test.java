package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.auth.api.MeController;
import com.example.BusinessLoanAPISpringBoot.auth.security.SecurityConfig;
import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Maps to documented test cases (closest implemented API):
 * - "Expired or tampered token is rejected with 401 and logged" (token rejection portion).
 *
 * Note: Logging/audit of auth failures isn't implemented in this MVP; this test validates 401 on unauthenticated access.
 */
@WebMvcTest(controllers = MeController.class)
@Import(SecurityConfig.class)
class test_me_controller_webmvc_test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("GET /api/me without Authorization header returns 401")
    void me_withoutAuth_is401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/me with valid JWT returns subject and authorities")
    void me_withAuth_is200() throws Exception {
        String token = "good-token";
        UUID userId = UUID.randomUUID();

        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("roles", List.class)).thenReturn(List.of("APPLICANT"));

        when(jwtService.parseAndValidate(anyString())).thenReturn(claims);

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(userId.toString()))
                .andExpect(jsonPath("$.authorities[0].authority").value("ROLE_APPLICANT"));
    }

    @Test
    @DisplayName("GET /api/me with invalid JWT yields 401 (JwtAuthFilter clears context)")
    void me_withInvalidJwt_is401() throws Exception {
        when(jwtService.parseAndValidate(anyString())).thenThrow(new RuntimeException("bad token"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }
}

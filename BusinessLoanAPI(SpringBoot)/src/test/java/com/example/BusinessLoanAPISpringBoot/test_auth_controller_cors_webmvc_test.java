package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.auth.api.AuthController;
import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import com.example.BusinessLoanAPISpringBoot.auth.security.SecurityConfig;
import com.example.BusinessLoanAPISpringBoot.auth.service.AuthService;
import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that browser cross-origin flows work for /api/auth/register.
 *
 * This test specifically protects against regressions where:
 * - OPTIONS preflight is blocked by Spring Security (causes browser "Network Error")
 * - Access-Control-Allow-* headers are missing due to missing CorsConfigurationSource
 */
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class test_auth_controller_cors_webmvc_test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    // SecurityConfig installs JwtAuthFilter which needs a JwtService bean (mocked for tests).
    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("OPTIONS /api/auth/register responds with CORS headers for allowed origin")
    void preflight_register_isAllowed() throws Exception {
        String origin = "https://vscode-internal-21566-beta.beta01.cloud.kavia.ai:3000";

        mockMvc.perform(
                        options("/api/auth/register")
                                .header(HttpHeaders.ORIGIN, origin)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                )
                // Spring may respond 200 or 204 depending on filter chain; accept any 2xx.
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.VARY, org.hamcrest.Matchers.containsString("Origin")));
    }

    @Test
    @DisplayName("POST /api/auth/register includes Access-Control-Allow-Origin for allowed origin")
    void post_register_includesCorsHeaders() throws Exception {
        String origin = "https://vscode-internal-21566-beta.beta01.cloud.kavia.ai:3000";

        AppUser created = new AppUser();
        created.setId(UUID.randomUUID());
        created.setEmail("user@example.com");
        created.setMfaEnabled(true);

        when(authService.registerApplicant(anyString(), anyString())).thenReturn(created);

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(HttpHeaders.ORIGIN, origin)
                                .content("{\"email\":\"user@example.com\",\"password\":\"Password123!\"}")
                )
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.mfaEnabled").value(true));
    }
}

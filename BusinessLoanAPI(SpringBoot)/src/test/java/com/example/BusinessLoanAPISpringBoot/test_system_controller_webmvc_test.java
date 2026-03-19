package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.auth.security.SecurityConfig;
import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import com.example.BusinessLoanAPISpringBoot.system.SystemController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Maps to documented test cases:
 * - "Health and readiness endpoints expose correct status" (component-level; implemented here as /health)
 *
 * Note: Actuator liveness/readiness endpoints are not loaded in @WebMvcTest slices; this test validates the
 * implemented SystemController health endpoint which is used for basic uptime checks.
 */
@WebMvcTest(controllers = SystemController.class)
@Import(SecurityConfig.class)
class test_system_controller_webmvc_test {

    @Autowired
    private MockMvc mockMvc;

    /**
     * We mock JwtService so SecurityConfig can instantiate JwtAuthFilter without requiring a real JWT_SECRET.
     */
    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("System endpoints are publicly accessible: GET / returns welcome message")
    void getRoot_isPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("Business Loan API is running"));
    }

    @Test
    @DisplayName("Health check is publicly accessible: GET /health returns OK")
    void getHealth_isPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("Info endpoint is publicly accessible: GET /api/info returns app info")
    void getInfo_isPublic() throws Exception {
        mockMvc.perform(get("/api/info").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string("Spring Boot Application: BusinessLoanAPISpringBoot"));
    }
}

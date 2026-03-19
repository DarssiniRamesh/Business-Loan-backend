package com.example.BusinessLoanAPISpringBoot.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal identity endpoint for debugging authentication.
 */
@RestController
@Tag(name = "Auth", description = "JWT authentication and MFA flows")
public class MeController {

    @GetMapping("/api/me")
    @Operation(summary = "Current user", description = "Returns basic info from the authenticated JWT subject/roles.")
    public Map<String, Object> me(Authentication auth) {
        return Map.of(
                "subject", auth.getName(),
                "authorities", auth.getAuthorities()
        );
    }
}

package com.example.BusinessLoanAPISpringBoot.auth.api;

import com.example.BusinessLoanAPISpringBoot.auth.api.dto.AuthDtos;
import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import com.example.BusinessLoanAPISpringBoot.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authentication endpoints (JWT + MFA).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "JWT authentication and MFA flows")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register applicant", description = "Creates a new applicant user account. MFA is enabled by default for MVP.")
    public ResponseEntity<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        AppUser user = authService.registerApplicant(req.email(), req.password());
        return ResponseEntity.ok(new AuthDtos.RegisterResponse(user.getId(), user.getEmail(), user.isMfaEnabled()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login (step 1)", description = "Validates credentials; if MFA enabled, issues OTP challenge and returns pendingMfa=true.")
    public ResponseEntity<AuthDtos.LoginStep1Response> loginStep1(@Valid @RequestBody AuthDtos.LoginStep1Request req) {
        AuthService.LoginStep1Result result = authService.loginStep1(req.email(), req.password());
        return ResponseEntity.ok(new AuthDtos.LoginStep1Response(result.userId(), result.pendingMfa(), result.devOtp()));
    }

    @PostMapping("/login/{userId}/mfa/verify")
    @Operation(summary = "Login (step 2)", description = "Verifies OTP for MFA and returns access/refresh tokens.")
    public ResponseEntity<AuthDtos.TokenResponse> verifyMfa(
            @PathVariable UUID userId,
            @Valid @RequestBody AuthDtos.VerifyMfaRequest req
    ) {
        AuthService.AuthTokens tokens = authService.loginStep2VerifyMfa(userId, req.otp());
        return ResponseEntity.ok(new AuthDtos.TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Rotates refresh token and returns a new access/refresh pair.")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        AuthService.AuthTokens tokens = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new AuthDtos.TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes the given refresh token if present.")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthDtos.LogoutRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

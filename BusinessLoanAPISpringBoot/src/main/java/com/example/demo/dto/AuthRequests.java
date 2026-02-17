package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class AuthRequests {
    // PUBLIC_INTERFACE
    @Schema(description = "Register request")
    public static class RegisterRequest {
        @Schema(description = "Email address", example = "user@example.com")
        public String email;
        @Schema(description = "Password", example = "Str0ngPa$$w0rd!")
        public String password;
    }

    // PUBLIC_INTERFACE
    @Schema(description = "Login request")
    public static class LoginRequest {
        @Schema(description = "Email address", example = "user@example.com")
        public String email;
        @Schema(description = "Password", example = "User password")
        public String password;
    }

    // PUBLIC_INTERFACE
    @Schema(description = "Resend verification request")
    public static class ResendVerificationRequest {
        @Schema(description = "Email address", example = "user@example.com")
        public String email;
    }

    // PUBLIC_INTERFACE
    @Schema(description = "Verify link request")
    public static class VerifyLinkRequest {
        @Schema(description = "Verification token sent via email")
        public String token;
    }
}

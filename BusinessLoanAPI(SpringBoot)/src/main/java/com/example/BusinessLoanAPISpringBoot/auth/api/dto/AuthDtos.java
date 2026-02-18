package com.example.BusinessLoanAPISpringBoot.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request/response DTOs for authentication endpoints.
 */
public class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record RegisterResponse(
            UUID userId,
            String email,
            boolean mfaEnabled
    ) {}

    public record LoginStep1Request(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record LoginStep1Response(
            UUID userId,
            boolean pendingMfa
    ) {}

    public record VerifyMfaRequest(
            @NotBlank String otp
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {}
}

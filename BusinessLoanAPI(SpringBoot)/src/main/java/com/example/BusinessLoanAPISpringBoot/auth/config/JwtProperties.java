package com.example.BusinessLoanAPISpringBoot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings loaded from environment variables / application properties.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {}

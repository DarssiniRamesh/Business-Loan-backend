package com.example.BusinessLoanAPISpringBoot.auth.service;

import com.example.BusinessLoanAPISpringBoot.auth.config.JwtProperties;
import com.example.BusinessLoanAPISpringBoot.auth.model.*;
import com.example.BusinessLoanAPISpringBoot.auth.repo.AppRoleRepository;
import com.example.BusinessLoanAPISpringBoot.auth.repo.AppUserRepository;
import com.example.BusinessLoanAPISpringBoot.auth.repo.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Core authentication logic: registration, login, MFA verification, refresh, logout.
 */
@Service
public class AuthService {

    private final AppUserRepository userRepo;
    private final AppRoleRepository roleRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final CryptoService cryptoService;
    private final JwtService jwtService;
    private final JwtProperties jwtProps;
    private final MfaService mfaService;

    public AuthService(
            AppUserRepository userRepo,
            AppRoleRepository roleRepo,
            RefreshTokenRepository refreshTokenRepo,
            CryptoService cryptoService,
            JwtService jwtService,
            JwtProperties jwtProps,
            MfaService mfaService
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.cryptoService = cryptoService;
        this.jwtService = jwtService;
        this.jwtProps = jwtProps;
        this.mfaService = mfaService;
    }

    @Transactional
    public AppUser registerApplicant(String email, String password) {
        userRepo.findByEmailIgnoreCase(email).ifPresent(u -> {
            throw new IllegalArgumentException("Email is already registered");
        });

        AppRole applicantRole = roleRepo.findByName(RoleName.APPLICANT)
                .orElseThrow(() -> new IllegalStateException("APPLICANT role missing; ensure migrations ran"));

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(cryptoService.hashPassword(password));
        user.getRoles().add(applicantRole);

        // For MVP, enable MFA by default (email-based OTP).
        user.setMfaEnabled(true);
        user.setMfaEmail(user.getEmail());

        return userRepo.save(user);
    }

    /**
     * Login step 1: validates credentials and triggers MFA if enabled.
     * Returns a "pendingMfa" flag and the userId to complete step 2.
     */
    @Transactional(readOnly = true)
    public LoginStep1Result loginStep1(String email, String password) {
        AppUser user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.isEnabled() || !cryptoService.matchesPassword(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (user.isMfaEnabled()) {
            mfaService.createChallenge(user, 300); // 5 minutes
            return new LoginStep1Result(user.getId(), true);
        }

        return new LoginStep1Result(user.getId(), false);
    }

    @Transactional
    public AuthTokens loginStep2VerifyMfa(UUID userId, String otp) {
        boolean ok = mfaService.verifyLatestChallenge(userId, otp);
        if (!ok) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return issueTokens(user);
    }

    @Transactional
    public AuthTokens issueTokens(AppUser user) {
        String accessToken = jwtService.issueAccessToken(user);

        // Refresh token: generate random UUID string, store hashed
        String refreshTokenRaw = UUID.randomUUID().toString() + "-" + UUID.randomUUID();
        String refreshTokenHash = cryptoService.sha256Base64(refreshTokenRaw);

        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUser(user);
        rt.setTokenHash(refreshTokenHash);
        rt.setExpiresAt(Instant.now().plusSeconds(jwtProps.refreshTokenTtlSeconds()));
        refreshTokenRepo.save(rt);

        return new AuthTokens(accessToken, refreshTokenRaw);
    }

    @Transactional
    public AuthTokens refresh(String refreshTokenRaw) {
        String hash = cryptoService.sha256Base64(refreshTokenRaw);
        RefreshToken rt = refreshTokenRepo.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (rt.getRevokedAt() != null || rt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        // Rotate refresh token: revoke old and issue new pair
        rt.setRevokedAt(Instant.now());
        refreshTokenRepo.save(rt);

        return issueTokens(rt.getUser());
    }

    @Transactional
    public void logout(String refreshTokenRaw) {
        String hash = cryptoService.sha256Base64(refreshTokenRaw);
        refreshTokenRepo.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            refreshTokenRepo.save(rt);
        });
    }

    public record LoginStep1Result(UUID userId, boolean pendingMfa) {}
    public record AuthTokens(String accessToken, String refreshToken) {}
}

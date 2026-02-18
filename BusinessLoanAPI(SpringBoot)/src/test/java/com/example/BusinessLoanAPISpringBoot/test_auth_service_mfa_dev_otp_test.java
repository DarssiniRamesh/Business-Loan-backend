package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.auth.config.JwtProperties;
import com.example.BusinessLoanAPISpringBoot.auth.config.MfaProperties;
import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import com.example.BusinessLoanAPISpringBoot.auth.repo.AppRoleRepository;
import com.example.BusinessLoanAPISpringBoot.auth.repo.AppUserRepository;
import com.example.BusinessLoanAPISpringBoot.auth.repo.RefreshTokenRepository;
import com.example.BusinessLoanAPISpringBoot.auth.service.AuthService;
import com.example.BusinessLoanAPISpringBoot.auth.service.CryptoService;
import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import com.example.BusinessLoanAPISpringBoot.auth.service.MfaService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the MFA dev fallback behavior.
 */
class test_auth_service_mfa_dev_otp_test {

    @Test
    void loginStep1_includesDevOtp_whenEnabled() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        AppRoleRepository roleRepo = mock(AppRoleRepository.class);
        RefreshTokenRepository refreshTokenRepo = mock(RefreshTokenRepository.class);
        CryptoService cryptoService = mock(CryptoService.class);
        JwtService jwtService = mock(JwtService.class);
        MfaService mfaService = mock(MfaService.class);

        JwtProperties jwtProps = new JwtProperties("secret", "issuer", 900, 1209600);

        MfaProperties mfaProps = new MfaProperties();
        mfaProps.setDevReturnOtp(true);

        AuthService authService = new AuthService(
                userRepo,
                roleRepo,
                refreshTokenRepo,
                cryptoService,
                jwtService,
                jwtProps,
                mfaService,
                mfaProps
        );

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setMfaEnabled(true);

        when(userRepo.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(cryptoService.matchesPassword(eq("Password123!"), eq("hash"))).thenReturn(true);
        when(mfaService.createChallenge(eq(user), anyLong())).thenReturn("123456");

        AuthService.LoginStep1Result result = authService.loginStep1("user@example.com", "Password123!");

        assertTrue(result.pendingMfa());
        assertEquals("123456", result.devOtp());
        verify(mfaService, times(1)).createChallenge(eq(user), anyLong());
    }

    @Test
    void loginStep1_excludesDevOtp_whenDisabled() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        AppRoleRepository roleRepo = mock(AppRoleRepository.class);
        RefreshTokenRepository refreshTokenRepo = mock(RefreshTokenRepository.class);
        CryptoService cryptoService = mock(CryptoService.class);
        JwtService jwtService = mock(JwtService.class);
        MfaService mfaService = mock(MfaService.class);

        JwtProperties jwtProps = new JwtProperties("secret", "issuer", 900, 1209600);

        MfaProperties mfaProps = new MfaProperties();
        mfaProps.setDevReturnOtp(false);

        AuthService authService = new AuthService(
                userRepo,
                roleRepo,
                refreshTokenRepo,
                cryptoService,
                jwtService,
                jwtProps,
                mfaService,
                mfaProps
        );

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setMfaEnabled(true);

        when(userRepo.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(cryptoService.matchesPassword(eq("Password123!"), eq("hash"))).thenReturn(true);
        when(mfaService.createChallenge(eq(user), anyLong())).thenReturn("123456");

        AuthService.LoginStep1Result result = authService.loginStep1("user@example.com", "Password123!");

        assertTrue(result.pendingMfa());
        assertNull(result.devOtp());
        verify(mfaService, times(1)).createChallenge(eq(user), anyLong());
    }
}

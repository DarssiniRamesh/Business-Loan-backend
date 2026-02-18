package com.example.BusinessLoanAPISpringBoot.auth.service;

import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import com.example.BusinessLoanAPISpringBoot.auth.model.MfaChallenge;
import com.example.BusinessLoanAPISpringBoot.auth.repo.MfaChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and verifies one-time MFA codes.
 *
 * Note: For MVP, delivery is logged unless SMTP env vars are configured (future enhancement).
 */
@Service
public class MfaService {
    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final MfaChallengeRepository repo;
    private final CryptoService cryptoService;
    private final SecureRandom random = new SecureRandom();

    public MfaService(MfaChallengeRepository repo, CryptoService cryptoService) {
        this.repo = repo;
        this.cryptoService = cryptoService;
    }

    public String createChallenge(AppUser user, long ttlSeconds) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        MfaChallenge c = new MfaChallenge();
        c.setId(UUID.randomUUID());
        c.setUser(user);
        c.setOtpHash(cryptoService.sha256Base64(otp));
        c.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        repo.save(c);

        // MVP delivery: log the OTP (replace with email/SMS provider integration).
        log.warn("MFA OTP for user {}: {}", user.getEmail(), otp);
        return otp;
    }

    public boolean verifyLatestChallenge(UUID userId, String otp) {
        MfaChallenge challenge = repo.findFirstByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .orElse(null);
        if (challenge == null) return false;

        String hash = cryptoService.sha256Base64(otp);
        if (!hash.equals(challenge.getOtpHash())) return false;

        challenge.setConsumedAt(Instant.now());
        repo.save(challenge);
        return true;
    }
}

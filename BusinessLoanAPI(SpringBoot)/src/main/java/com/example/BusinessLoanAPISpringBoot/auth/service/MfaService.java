package com.example.BusinessLoanAPISpringBoot.auth.service;

import com.example.BusinessLoanAPISpringBoot.auth.config.MfaProperties;
import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import com.example.BusinessLoanAPISpringBoot.auth.model.MfaChallenge;
import com.example.BusinessLoanAPISpringBoot.auth.repo.MfaChallengeRepository;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationEventType;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.EmailProvider;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and verifies one-time MFA codes.
 *
 * Delivery behavior:
 * - Attempts email delivery via configured notification providers (stub/sendgrid).
 * - Provides a configurable dev fallback via app.mfa.dev-return-otp (returned by /api/auth/login).
 * - Can optionally log OTP on delivery failure via app.mfa.log-otp-on-failure (NOT recommended in prod).
 */
@Service
public class MfaService {
    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final MfaChallengeRepository repo;
    private final CryptoService cryptoService;
    private final MfaProperties mfaProps;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;

    private final SecureRandom random = new SecureRandom();

    public MfaService(
            MfaChallengeRepository repo,
            CryptoService cryptoService,
            MfaProperties mfaProps,
            EmailProvider emailProvider,
            SmsProvider smsProvider
    ) {
        this.repo = repo;
        this.cryptoService = cryptoService;
        this.mfaProps = mfaProps;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
    }

    public String createChallenge(AppUser user, long ttlSeconds) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        MfaChallenge c = new MfaChallenge();
        c.setId(UUID.randomUUID());
        c.setUser(user);
        c.setOtpHash(cryptoService.sha256Base64(otp));
        c.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        repo.save(c);

        // Best-effort delivery: email is the primary channel in this MVP.
        if (mfaProps.isDeliveryEnabled()) {
            boolean delivered = deliverBestEffort(user, otp, ttlSeconds);
            if (!delivered && mfaProps.isLogOtpOnFailure()) {
                // Last-resort fallback (insecure): helpful in CI/dev when delivery isn't configured.
                log.warn("MFA OTP delivery failed; last-resort OTP log for user {} otp={}", user.getEmail(), otp);
            }
        } else {
            log.debug("MFA OTP generated but delivery is disabled. userId={}", user.getId());
            if (mfaProps.isLogOtpOnFailure()) {
                log.warn("MFA OTP (delivery disabled, logOtpOnFailure=true) user={} otp={}", user.getEmail(), otp);
            }
        }

        return otp;
    }

    private boolean deliverBestEffort(AppUser user, String otp, long ttlSeconds) {
        String toEmail = user.getMfaEmail();
        if (toEmail == null || toEmail.isBlank()) {
            toEmail = user.getEmail();
        }

        String subject = "Your Business Loan verification code";
        String body = "Your verification code is: " + otp + "\n\n"
                + "It expires in " + Math.max(0, ttlSeconds) + " seconds.\n"
                + "If you did not request this code, you can ignore this message.\n";

        NotificationRequest req = new NotificationRequest(
                NotificationEventType.MFA_OTP,
                null,
                user.getId(),
                toEmail,
                null,
                null,
                null,
                Instant.now()
        );

        // Email (primary)
        if (toEmail != null && !toEmail.isBlank()) {
            try {
                emailProvider.send(req, toEmail.trim(), subject, body);
                return true;
            } catch (Exception e) {
                log.warn("MFA OTP email delivery failed (ignored). userId={} error={}", user.getId(), e.toString());
            }
        }

        // SMS (future) - no phone number stored in MVP schema, but wiring is kept for completeness.
        // If you add phone storage later, set applicantPhone and call smsProvider here.

        return false;
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

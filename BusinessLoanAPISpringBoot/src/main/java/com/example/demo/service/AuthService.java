package com.example.demo.service;

import com.example.demo.entity.AuditLog;
import com.example.demo.entity.EmailVerificationToken;
import com.example.demo.entity.User;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.EmailVerificationTokenRepository;
import com.example.demo.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

// PUBLIC_INTERFACE
@Service
public class AuthService {
    private final UserRepository userRepo;
    private final EmailVerificationTokenRepository tokenRepo;
    private final AuditLogRepository auditRepo;

    @Value("${app.auth.emailTokenMinutes:10}")
    private int emailTokenExpiryMinutes;

    public AuthService(UserRepository userRepo,
                       EmailVerificationTokenRepository tokenRepo,
                       AuditLogRepository auditRepo) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.auditRepo = auditRepo;
    }

    // PUBLIC_INTERFACE
    @Transactional
    public void registerUser(String email, String plainPassword, String requestIp) {
        if (userRepo.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered.");
        }
        String passwordHash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        User user = new User(email, passwordHash);
        userRepo.save(user);
        auditRepo.save(new AuditLog("REGISTER", email, requestIp, null));
        sendOrResendVerification(user, requestIp, true);
    }

    // PUBLIC_INTERFACE
    @Transactional
    public void sendOrResendVerification(User user, String requestIp, boolean isNew) {
        // Invalidate old tokens
        tokenRepo.deleteByUserId(user.getId());
        // Generate new token
        String tokenValue = Base64.getUrlEncoder().withoutPadding()
                 .encodeToString(UUID.randomUUID().toString().getBytes());
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(emailTokenExpiryMinutes);

        EmailVerificationToken token = new EmailVerificationToken(tokenValue, user.getId(), expiry);
        tokenRepo.save(token);
        String action = isNew ? "VERIFICATION_SENT" : "VERIFICATION_RESENT";
        auditRepo.save(new AuditLog(action, user.getEmail(), requestIp, "token=" + tokenValue));
        // (Trigger email sending here; not implemented)
    }

    // PUBLIC_INTERFACE
    @Transactional
    public boolean verifyToken(String tokenValue, String requestIp) {
        Optional<EmailVerificationToken> tokenOpt = tokenRepo.findByToken(tokenValue);
        if (tokenOpt.isEmpty()) return false;
        EmailVerificationToken token = tokenOpt.get();
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            auditRepo.save(new AuditLog("VERIFICATION_FAILED", null, requestIp, "token=" + tokenValue));
            return false;
        }
        Optional<User> userOpt = userRepo.findById(token.getUserId());
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        user.setEmailVerified(true);
        userRepo.save(user);
        token.setUsed(true);
        tokenRepo.save(token);

        auditRepo.save(new AuditLog("VERIFIED", user.getEmail(), requestIp, "success"));
        return true;
    }

    // PUBLIC_INTERFACE
    public boolean canLogin(String email, String plainPassword, String sourceIp) {
        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            auditRepo.save(new AuditLog("LOGIN_ATTEMPT_BAD_EMAIL", email, sourceIp, null));
            return false;
        }
        User user = userOpt.get();
        if (!user.isEmailVerified()) {
            auditRepo.save(new AuditLog("LOGIN_ATTEMPT_NOT_VERIFIED", email, sourceIp, null));
            return false;
        }
        if (!BCrypt.checkpw(plainPassword, user.getPasswordHash())) {
            auditRepo.save(new AuditLog("LOGIN_ATTEMPT_BAD_PASSWORD", email, sourceIp, null));
            return false;
        }
        auditRepo.save(new AuditLog("LOGIN_SUCCESS", email, sourceIp, null));
        return true;
    }

    // PUBLIC_INTERFACE
    public Optional<User> getUserByEmail(String email) {
        return userRepo.findByEmail(email);
    }
}

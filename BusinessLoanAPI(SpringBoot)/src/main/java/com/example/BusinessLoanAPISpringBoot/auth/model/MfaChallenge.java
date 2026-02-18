package com.example.BusinessLoanAPISpringBoot.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * MFA one-time-password challenge (stored hashed).
 */
@Entity
@Table(name = "mfa_challenge")
public class MfaChallenge {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public MfaChallenge() {}

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}

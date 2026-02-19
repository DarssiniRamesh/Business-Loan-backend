package com.example.BusinessLoanAPISpringBoot.auth.repo;

import com.example.BusinessLoanAPISpringBoot.auth.model.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * MFA challenge repository.
 */
public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {
    Optional<MfaChallenge> findFirstByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, Instant now);
}

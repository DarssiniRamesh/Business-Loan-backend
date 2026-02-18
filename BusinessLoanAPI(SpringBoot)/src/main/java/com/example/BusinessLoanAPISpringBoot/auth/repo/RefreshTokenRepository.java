package com.example.BusinessLoanAPISpringBoot.auth.repo;

import com.example.BusinessLoanAPISpringBoot.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token repository.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}

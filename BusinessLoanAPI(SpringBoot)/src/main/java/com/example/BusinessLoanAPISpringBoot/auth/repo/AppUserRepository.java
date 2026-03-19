package com.example.BusinessLoanAPISpringBoot.auth.repo;

import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * User repository.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
}

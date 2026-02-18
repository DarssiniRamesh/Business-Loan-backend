package com.example.BusinessLoanAPISpringBoot.auth.repo;

import com.example.BusinessLoanAPISpringBoot.auth.model.AppRole;
import com.example.BusinessLoanAPISpringBoot.auth.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Role repository.
 */
public interface AppRoleRepository extends JpaRepository<AppRole, UUID> {
    Optional<AppRole> findByName(RoleName name);
}

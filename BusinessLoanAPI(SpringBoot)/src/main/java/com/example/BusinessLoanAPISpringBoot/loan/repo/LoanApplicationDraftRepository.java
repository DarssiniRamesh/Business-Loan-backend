package com.example.BusinessLoanAPISpringBoot.loan.repo;

import com.example.BusinessLoanAPISpringBoot.loan.model.LoanApplicationDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for loan application drafts.
 */
public interface LoanApplicationDraftRepository extends JpaRepository<LoanApplicationDraft, UUID> {

    Optional<LoanApplicationDraft> findByIdAndUserId(UUID id, UUID userId);

    List<LoanApplicationDraft> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);
}

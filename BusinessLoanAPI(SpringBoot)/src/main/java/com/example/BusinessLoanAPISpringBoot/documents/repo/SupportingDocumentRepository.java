package com.example.BusinessLoanAPISpringBoot.documents.repo;

import com.example.BusinessLoanAPISpringBoot.documents.model.SupportingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for supporting document metadata.
 */
public interface SupportingDocumentRepository extends JpaRepository<SupportingDocument, UUID> {

    List<SupportingDocument> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SupportingDocument> findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(UUID userId, UUID loanDraftId);

    Optional<SupportingDocument> findByIdAndUserId(UUID id, UUID userId);
}

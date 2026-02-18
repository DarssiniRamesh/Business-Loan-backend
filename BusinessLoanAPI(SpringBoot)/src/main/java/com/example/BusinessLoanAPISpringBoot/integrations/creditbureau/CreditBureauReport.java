package com.example.BusinessLoanAPISpringBoot.integrations.creditbureau;

/**
 * Provider-agnostic credit report summary used by the decisioning workflow.
 *
 * Fields:
 * - provider: which provider generated this report (e.g., "stub")
 * - creditScore: a typical bureau score range (300..850). Null means unavailable.
 * - identityMatch: whether applicant identity matched bureau records (null if not checked)
 * - notes: human-readable notes safe for audit logging (no secrets)
 */
public record CreditBureauReport(
        String provider,
        Integer creditScore,
        Boolean identityMatch,
        String notes
) { }

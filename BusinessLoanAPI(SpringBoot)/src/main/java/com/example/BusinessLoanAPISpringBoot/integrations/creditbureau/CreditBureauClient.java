package com.example.BusinessLoanAPISpringBoot.integrations.creditbureau;

/**
 * Abstraction for external credit bureau lookups/validations.
 *
 * MVP notes:
 * - This interface intentionally supports a stub provider so the application can run without real credentials.
 * - A real provider can be added later without changing the decisioning workflow call sites.
 */
public interface CreditBureauClient {

    // PUBLIC_INTERFACE
    CreditBureauReport fetchReport(CreditBureauRequest request);
    /** Fetch a credit bureau report for the given applicant/business identifiers. */
}

package com.example.BusinessLoanAPISpringBoot.integrations.creditbureau;

import java.util.Objects;

/**
 * Minimal request model for a credit bureau lookup.
 *
 * We keep this deliberately small and not tied to any bureau-specific schema.
 */
public record CreditBureauRequest(
        String applicantFirstName,
        String applicantLastName,
        String applicantSsnLast4,
        String businessName,
        String businessEinLast4
) {
    public CreditBureauRequest {
        // Normalize blank strings to null for consistent provider behavior.
        applicantFirstName = normalize(applicantFirstName);
        applicantLastName = normalize(applicantLastName);
        applicantSsnLast4 = normalize(applicantSsnLast4);
        businessName = normalize(businessName);
        businessEinLast4 = normalize(businessEinLast4);
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // PUBLIC_INTERFACE
    public boolean hasAnyIdentifier() {
        /** Returns true if at least one identifier is present; useful for deciding if a lookup should be attempted. */
        return (applicantFirstName != null || applicantLastName != null || applicantSsnLast4 != null
                || businessName != null || businessEinLast4 != null);
    }

    // PUBLIC_INTERFACE
    public String stableKey() {
        /**
         * Returns a stable, non-sensitive key that can be used for deterministic stub behavior.
         *
         * IMPORTANT: This intentionally avoids returning full SSN/EIN and is safe to log.
         */
        return Objects.toString(applicantLastName, "") + "|"
                + Objects.toString(applicantSsnLast4, "") + "|"
                + Objects.toString(businessName, "") + "|"
                + Objects.toString(businessEinLast4, "");
    }
}

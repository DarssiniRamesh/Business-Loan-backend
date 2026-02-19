package com.example.BusinessLoanAPISpringBoot.integrations.creditbureau;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Stub credit bureau client for local/dev/preview environments.
 *
 * Behavior:
 * - Deterministically generates a pseudo credit score based on a stable key derived from provided identifiers.
 * - Does NOT call any external services.
 * - Safe to use without credentials.
 */
@Component
public class StubCreditBureauClient implements CreditBureauClient {

    @Override
    public CreditBureauReport fetchReport(CreditBureauRequest request) {
        if (request == null || !request.hasAnyIdentifier()) {
            return new CreditBureauReport(
                    "stub",
                    null,
                    null,
                    "No identifiers provided; stub bureau did not generate a score."
            );
        }

        int score = deterministicScore(request.stableKey());
        boolean match = score >= 520; // arbitrary but deterministic rule for MVP

        return new CreditBureauReport(
                "stub",
                score,
                match,
                "Stub bureau report generated deterministically from non-sensitive identifiers."
        );
    }

    private int deterministicScore(String key) {
        CRC32 crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        long v = crc.getValue();

        // Map crc (0..2^32-1) into typical bureau range 300..850 inclusive.
        int min = 300;
        int max = 850;
        int range = (max - min) + 1;
        return min + (int) (v % range);
    }
}

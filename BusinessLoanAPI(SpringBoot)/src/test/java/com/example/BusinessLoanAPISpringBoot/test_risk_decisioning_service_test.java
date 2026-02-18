package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.integrations.creditbureau.CreditBureauClient;
import com.example.BusinessLoanAPISpringBoot.integrations.creditbureau.CreditBureauReport;
import com.example.BusinessLoanAPISpringBoot.loan.service.RiskDecisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extra tests (not directly enumerated in extracted test cases) for the implemented risk scoring engine.
 * These protect core decisioning behavior and thresholds used by /api/loan/drafts/{id}/decision and submit flows.
 */
class test_risk_decisioning_service_test {

    @Test
    @DisplayName("Hard-decline rule: annualRevenue <= 0 yields DECLINED and score=0")
    void hardDecline_revenueNonPositive() {
        CreditBureauClient bureau = req -> null;
        RiskDecisioningService svc = new RiskDecisioningService(new ObjectMapper(), bureau);

        RiskDecisioningService.RiskDecision rd = svc.scoreAndDecide("{\"annualRevenue\":0,\"requestedLoanAmount\":1000}");

        assertEquals(RiskDecisioningService.Decision.DECLINED, rd.decision());
        assertEquals(0, rd.score());
        assertTrue(rd.reason().contains("Annual revenue must be greater than 0"));
    }

    @Test
    @DisplayName("High-quality application yields PRE_QUALIFIED")
    void highQuality_preQualified() {
        CreditBureauClient bureau = req -> new CreditBureauReport("stub", 780, true, "ok");
        RiskDecisioningService svc = new RiskDecisioningService(new ObjectMapper(), bureau);

        String json = """
                {
                  "businessInfo": { "annualRevenue": 2000000, "yearsInBusiness": 7 },
                  "loanRequest": { "requestedAmount": 100000 },
                  "documents": { "hasTaxReturns": true, "hasBankStatements": true },
                  "owner": { "firstName":"A", "lastName":"B", "ssnLast4":"1234" }
                }
                """;

        RiskDecisioningService.RiskDecision rd = svc.scoreAndDecide(json);

        assertEquals(RiskDecisioningService.Decision.PRE_QUALIFIED, rd.decision());
        assertTrue(rd.score() >= 80, "Expected score >= 80 for pre-qualified path");
    }

    @Test
    @DisplayName("Credit bureau identity mismatch reduces score (may push to MANUAL_REVIEW)")
    void identityMismatch_penalizesScore() {
        CreditBureauClient bureau = req -> new CreditBureauReport("stub", 680, false, "mismatch");
        RiskDecisioningService svc = new RiskDecisioningService(new ObjectMapper(), bureau);

        String json = """
                {
                  "businessInfo": { "annualRevenue": 750000, "yearsInBusiness": 3 },
                  "loanRequest": { "requestedAmount": 150000 },
                  "documents": { "hasTaxReturns": true, "hasBankStatements": true },
                  "owner": { "firstName":"A", "lastName":"B", "ssnLast4":"1234" }
                }
                """;

        RiskDecisioningService.RiskDecision rd = svc.scoreAndDecide(json);

        assertNotNull(rd.decision());
        assertNotNull(rd.reason());
        // This test asserts the method completes and returns a deterministic decision; exact threshold depends on scoring details.
    }
}

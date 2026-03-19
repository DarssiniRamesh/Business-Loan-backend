package com.example.BusinessLoanAPISpringBoot.loan.service;

import com.example.BusinessLoanAPISpringBoot.integrations.creditbureau.CreditBureauClient;
import com.example.BusinessLoanAPISpringBoot.integrations.creditbureau.CreditBureauReport;
import com.example.BusinessLoanAPISpringBoot.integrations.creditbureau.CreditBureauRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Risk scoring and decisioning engine for loan applications.
 *
 * Notes:
 * - MVP implementation uses self-reported draft JSON plus (optional) credit bureau integration via an abstraction.
 * - Produces a numeric risk score (0-100; higher is better) plus a decision:
 *   PRE_QUALIFIED / MANUAL_REVIEW / DECLINED.
 * - This is intentionally deterministic and explainable for audit and iteration.
 */
@Service
public class RiskDecisioningService {

    private final ObjectMapper objectMapper;
    private final CreditBureauClient creditBureauClient;

    public RiskDecisioningService(ObjectMapper objectMapper, CreditBureauClient creditBureauClient) {
        this.objectMapper = objectMapper;
        this.creditBureauClient = creditBureauClient;
    }

    public enum Decision {
        PRE_QUALIFIED,
        MANUAL_REVIEW,
        DECLINED
    }

    public record RiskDecision(
            int score,
            Decision decision,
            String reason
    ) {}

    // PUBLIC_INTERFACE
    public RiskDecision scoreAndDecide(String draftDataJson) {
        /** Compute risk score and decision from the stored draft JSON payload. */
        JsonNode root = readJsonOrEmptyObject(draftDataJson);

        // We look for common MVP fields in a few likely locations to be resilient to front-end shape changes.
        BigDecimal annualRevenue = firstDecimal(
                root.at("/businessInfo/annualRevenue"),
                root.at("/business/annualRevenue"),
                root.at("/annualRevenue"),
                root.at("/revenueAnnual"),
                root.at("/revenue")
        );

        BigDecimal requestedLoanAmount = firstDecimal(
                root.at("/loanRequest/requestedAmount"),
                root.at("/loan/amountRequested"),
                root.at("/requestedLoanAmount"),
                root.at("/loanAmount")
        );

        Integer yearsInBusiness = firstInteger(
                root.at("/businessInfo/yearsInBusiness"),
                root.at("/business/yearsInBusiness"),
                root.at("/yearsInBusiness")
        );

        boolean hasTaxReturns = firstBoolean(
                root.at("/documents/hasTaxReturns"),
                root.at("/supportingDocuments/hasTaxReturns")
        );

        boolean hasBankStatements = firstBoolean(
                root.at("/documents/hasBankStatements"),
                root.at("/supportingDocuments/hasBankStatements")
        );

        // --- Optional credit bureau augmentation (via integration abstraction) ---
        CreditBureauReport bureauReport = null;
        try {
            CreditBureauRequest bureauRequest = extractCreditBureauRequest(root);
            bureauReport = (bureauRequest != null && bureauRequest.hasAnyIdentifier())
                    ? creditBureauClient.fetchReport(bureauRequest)
                    : null;
        } catch (Exception ignored) {
            // Integration errors must never break decisioning in MVP; we proceed without bureau input.
            bureauReport = null;
        }

        // --- Hard-decline rules (fast, explainable) ---
        if (annualRevenue != null && annualRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskDecision(0, Decision.DECLINED, "Annual revenue must be greater than 0.");
        }
        if (requestedLoanAmount != null && requestedLoanAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskDecision(0, Decision.DECLINED, "Requested loan amount must be greater than 0.");
        }
        if (yearsInBusiness != null && yearsInBusiness < 0) {
            return new RiskDecision(0, Decision.DECLINED, "Years in business cannot be negative.");
        }

        // --- Score components ---
        // Base score starts at 50 and is adjusted by multiple factors.
        int score = 50;

        // Revenue factor
        if (annualRevenue != null) {
            if (annualRevenue.compareTo(new BigDecimal("2000000")) >= 0) {
                score += 20;
            } else if (annualRevenue.compareTo(new BigDecimal("750000")) >= 0) {
                score += 12;
            } else if (annualRevenue.compareTo(new BigDecimal("250000")) >= 0) {
                score += 5;
            } else {
                score -= 10;
            }
        } else {
            // Missing revenue increases uncertainty
            score -= 12;
        }

        // Years in business factor
        if (yearsInBusiness != null) {
            if (yearsInBusiness >= 5) {
                score += 10;
            } else if (yearsInBusiness >= 2) {
                score += 4;
            } else {
                score -= 8;
            }
        } else {
            score -= 6;
        }

        // Leverage factor: requested / revenue (lower is better)
        if (annualRevenue != null && requestedLoanAmount != null && annualRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = requestedLoanAmount.divide(annualRevenue, 6, BigDecimal.ROUND_HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.10")) <= 0) {
                score += 10;
            } else if (ratio.compareTo(new BigDecimal("0.25")) <= 0) {
                score += 2;
            } else if (ratio.compareTo(new BigDecimal("0.50")) <= 0) {
                score -= 8;
            } else {
                score -= 18;
            }
        } else if (requestedLoanAmount != null) {
            // Missing revenue but asked for money: more risk
            score -= 10;
        }

        // Documents completeness factor (self-reported flags until docs integration is wired in)
        int docs = 0;
        if (hasTaxReturns) docs++;
        if (hasBankStatements) docs++;

        if (docs == 2) {
            score += 6;
        } else if (docs == 1) {
            score += 0;
        } else {
            score -= 6;
        }

        // Credit bureau factor (if available): map score 300..850 into a small adjustment.
        // This is intentionally lightweight in MVP and can be replaced by a proper model later.
        if (bureauReport != null && bureauReport.creditScore() != null) {
            int cs = bureauReport.creditScore();
            if (cs >= 740) {
                score += 10;
            } else if (cs >= 680) {
                score += 5;
            } else if (cs >= 620) {
                score += 0;
            } else if (cs >= 560) {
                score -= 6;
            } else {
                score -= 12;
            }

            // Identity mismatch increases uncertainty; push toward manual review.
            if (Boolean.FALSE.equals(bureauReport.identityMatch())) {
                score -= 8;
            }
        }

        // Clamp to 0..100
        score = Math.max(0, Math.min(100, score));

        // --- Decision thresholds ---
        // 80-100: Pre-Qualified
        // 55-79: Manual Review
        // 0-54: Declined
        Decision decision;
        if (score >= 80) {
            decision = Decision.PRE_QUALIFIED;
        } else if (score >= 55) {
            decision = Decision.MANUAL_REVIEW;
        } else {
            decision = Decision.DECLINED;
        }

        String reason = buildReason(score, decision, annualRevenue, requestedLoanAmount, yearsInBusiness, docs, bureauReport);
        return new RiskDecision(score, decision, reason);
    }

    private String buildReason(
            int score,
            Decision decision,
            BigDecimal annualRevenue,
            BigDecimal requestedLoanAmount,
            Integer yearsInBusiness,
            int docs,
            CreditBureauReport bureauReport
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Score=").append(score).append(". Decision=").append(decision).append(". ");

        if (annualRevenue == null) {
            sb.append("Missing annual revenue. ");
        } else {
            sb.append("Annual revenue=").append(annualRevenue).append(". ");
        }

        if (requestedLoanAmount == null) {
            sb.append("Missing requested loan amount. ");
        } else {
            sb.append("Requested amount=").append(requestedLoanAmount).append(". ");
        }

        if (yearsInBusiness == null) {
            sb.append("Missing years in business. ");
        } else {
            sb.append("Years in business=").append(yearsInBusiness).append(". ");
        }

        sb.append("Docs provided flags=").append(docs).append("/2. ");

        if (bureauReport == null) {
            sb.append("Credit bureau=not used/available.");
        } else if (bureauReport.creditScore() == null) {
            sb.append("Credit bureau=").append(bureauReport.provider()).append(" (no score).");
        } else {
            sb.append("Credit bureau=").append(bureauReport.provider())
                    .append(" score=").append(bureauReport.creditScore()).append(".");
            if (bureauReport.identityMatch() != null) {
                sb.append(" identityMatch=").append(bureauReport.identityMatch()).append(".");
            }
        }

        // Provide some interpretation hints for manual review cases.
        if (decision == Decision.MANUAL_REVIEW) {
            sb.append(" Manual review triggered due to moderate score or missing/uncertain inputs.");
        }
        return sb.toString();
    }

    private CreditBureauRequest extractCreditBureauRequest(JsonNode root) {
        // Keep extraction resilient: accept likely shapes, avoid hard requirements.
        String first = firstText(
                root.at("/owner/firstName"),
                root.at("/ownerInfo/firstName"),
                root.at("/applicant/firstName"),
                root.at("/firstName")
        );
        String last = firstText(
                root.at("/owner/lastName"),
                root.at("/ownerInfo/lastName"),
                root.at("/applicant/lastName"),
                root.at("/lastName")
        );

        // Expect last4 only if provided by UI; do not attempt to parse full SSN.
        String ssnLast4 = firstText(
                root.at("/owner/ssnLast4"),
                root.at("/ownerInfo/ssnLast4"),
                root.at("/applicant/ssnLast4"),
                root.at("/ssnLast4")
        );

        String businessName = firstText(
                root.at("/businessInfo/legalName"),
                root.at("/business/legalName"),
                root.at("/businessInfo/name"),
                root.at("/business/name")
        );

        String einLast4 = firstText(
                root.at("/businessInfo/einLast4"),
                root.at("/business/einLast4"),
                root.at("/einLast4")
        );

        return new CreditBureauRequest(first, last, ssnLast4, businessName, einLast4);
    }

    private String firstText(JsonNode... candidates) {
        for (JsonNode n : candidates) {
            if (n == null || n.isMissingNode() || n.isNull()) continue;
            if (n.isTextual()) {
                String s = n.asText().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private JsonNode readJsonOrEmptyObject(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            JsonNode node = objectMapper.readTree(json);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            // If draft data is invalid JSON (shouldn't happen due to existing validations),
            // treat as empty to avoid breaking workflow; decision will likely be lower + manual/decline.
            return objectMapper.createObjectNode();
        }
    }

    private BigDecimal firstDecimal(JsonNode... candidates) {
        for (JsonNode n : candidates) {
            if (n == null || n.isMissingNode() || n.isNull()) continue;
            if (n.isNumber()) return n.decimalValue();
            if (n.isTextual()) {
                String s = n.asText().trim();
                if (s.isEmpty()) continue;
                try {
                    return new BigDecimal(s.replaceAll("[,$]", ""));
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode... candidates) {
        for (JsonNode n : candidates) {
            if (n == null || n.isMissingNode() || n.isNull()) continue;
            if (n.isInt() || n.isLong()) return n.asInt();
            if (n.isTextual()) {
                String s = n.asText().trim();
                if (s.isEmpty()) continue;
                try {
                    return Integer.parseInt(s);
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private boolean firstBoolean(JsonNode... candidates) {
        for (JsonNode n : candidates) {
            if (n == null || n.isMissingNode() || n.isNull()) continue;
            if (n.isBoolean()) return n.asBoolean();
            if (n.isTextual()) {
                String s = n.asText().trim().toLowerCase();
                if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
                if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
            }
        }
        return false;
    }
}

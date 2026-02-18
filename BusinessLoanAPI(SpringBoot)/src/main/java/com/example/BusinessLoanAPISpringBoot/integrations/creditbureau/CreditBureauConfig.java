package com.example.BusinessLoanAPISpringBoot.integrations.creditbureau;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for credit bureau integration.
 */
@Configuration
@EnableConfigurationProperties(CreditBureauProperties.class)
public class CreditBureauConfig {

    @Bean
    public CreditBureauClient creditBureauClient(
            CreditBureauProperties properties,
            StubCreditBureauClient stubCreditBureauClient
    ) {
        if (!properties.isEnabled()) {
            // Return a no-op implementation if integration disabled (still keeps workflow simple).
            return request -> new CreditBureauReport(
                    "disabled",
                    null,
                    null,
                    "Credit bureau integration disabled by configuration."
            );
        }

        String provider = properties.getProvider() == null ? "stub" : properties.getProvider().trim().toLowerCase();
        if (provider.isEmpty() || provider.equals("stub")) {
            return stubCreditBureauClient;
        }

        // Future: add real implementations here.
        throw new IllegalStateException(
                "Unsupported credit bureau provider '" + properties.getProvider() + "'. Supported providers: stub"
        );
    }
}

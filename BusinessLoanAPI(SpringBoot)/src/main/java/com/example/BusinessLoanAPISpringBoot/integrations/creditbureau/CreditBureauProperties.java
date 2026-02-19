package com.example.BusinessLoanAPISpringBoot.integrations.creditbureau;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for credit bureau integration.
 */
@ConfigurationProperties(prefix = "app.credit-bureau")
public class CreditBureauProperties {

    /**
     * Provider id, e.g. "stub" (default) or "experian"/"equifax" in future.
     */
    private String provider = "stub";

    /**
     * Whether bureau lookups are enabled. If false, decisioning proceeds without bureau data.
     */
    private boolean enabled = true;

    public String getProvider() {
        return provider;
    }

    public CreditBureauProperties setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CreditBureauProperties setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}

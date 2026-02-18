package com.example.BusinessLoanAPISpringBoot.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Ensures {@code spring.datasource.url} is set from {@code DATABASE_URL} when the provided value is already a JDBC URL.
 *
 * <p>This project now expects {@code DATABASE_URL} to be a JDBC-style URL (e.g. {@code jdbc:postgresql://...})
 * as provided by the deployment environment. Any URI-to-JDBC conversion logic has been intentionally removed to
 * avoid surprising rewrites and to ensure Flyway/JPA start up using the exact Neon JDBC URL provided.</p>
 *
 * <p>Precedence rules:</p>
 * <ul>
 *   <li>If {@code spring.datasource.url} is already set (properties/env), this processor does nothing.</li>
 *   <li>If {@code DATABASE_URL} is missing/blank, this processor does nothing.</li>
 *   <li>If {@code DATABASE_URL} does not start with {@code jdbc:}, this processor does nothing (Spring will later
 *       fail with a clear configuration error if DB is required).</li>
 * </ul>
 */
public final class NeonDatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (!StringUtils.hasText(databaseUrl)) {
            return;
        }

        // If user already provided spring.datasource.url explicitly, do not override it.
        String existingJdbc = environment.getProperty("spring.datasource.url");
        if (StringUtils.hasText(existingJdbc)) {
            return;
        }

        String trimmed = databaseUrl.trim();
        if (!trimmed.startsWith("jdbc:")) {
            // We now rely on DATABASE_URL being a JDBC URL directly.
            return;
        }

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", trimmed);

        // Keep username/password resolution as-is: Spring will use spring.datasource.username/password
        // if present, otherwise the driver can also parse user/password from the JDBC query parameters.
        environment.getPropertySources().addFirst(new MapPropertySource("neonDatasource", props));
    }

    @Override
    public int getOrder() {
        // Run early so DataSourceAutoConfiguration sees the normalized properties.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

package com.example.BusinessLoanAPISpringBoot.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes Neon / platform-provided Postgres connection strings so Spring Boot can configure a JDBC DataSource.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>{@code DATABASE_URL} in JDBC form: {@code jdbc:postgresql://host:5432/db?sslmode=require}</li>
 *   <li>{@code DATABASE_URL} in URI form (common on Neon/Heroku-style envs):
 *       {@code postgresql://user:pass@host:5432/db?sslmode=require}</li>
 * </ul>
 *
 * <p>If {@code DATABASE_URL} is in URI form, this processor will:</p>
 * <ul>
 *   <li>Convert it to a JDBC URL and set {@code spring.datasource.url}</li>
 *   <li>If username/password are embedded in the URI and {@code spring.datasource.username/password}
 *       are not already set, extract and set them.</li>
 * </ul>
 *
 * <p>This keeps existing explicit configuration (e.g. {@code spring.datasource.url} or
 * {@code DATABASE_USERNAME/DATABASE_PASSWORD}) as the higher-precedence source.</p>
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
        if (trimmed.startsWith("jdbc:")) {
            // Already a JDBC URL; just map it through for consistency.
            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", trimmed);
            environment.getPropertySources().addFirst(new MapPropertySource("neonDatasource", props));
            return;
        }

        if (trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://")) {
            Map<String, Object> props = new HashMap<>();
            try {
                URI uri = URI.create(trimmed.replaceFirst("^postgres://", "postgresql://"));
                String host = uri.getHost();
                int port = (uri.getPort() > 0) ? uri.getPort() : 5432;

                String path = uri.getPath() == null ? "" : uri.getPath();
                String db = path.startsWith("/") ? path.substring(1) : path;

                String query = uri.getQuery();
                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db
                        + (StringUtils.hasText(query) ? "?" + query : "");

                props.put("spring.datasource.url", jdbcUrl);

                // Only set username/password if not already present via env/properties.
                if (!StringUtils.hasText(environment.getProperty("spring.datasource.username"))
                        && !StringUtils.hasText(environment.getProperty("DATABASE_USERNAME"))) {
                    String user = extractUser(uri);
                    if (StringUtils.hasText(user)) {
                        props.put("spring.datasource.username", user);
                    }
                }
                if (!StringUtils.hasText(environment.getProperty("spring.datasource.password"))
                        && !StringUtils.hasText(environment.getProperty("DATABASE_PASSWORD"))) {
                    String pass = extractPassword(uri);
                    if (StringUtils.hasText(pass)) {
                        props.put("spring.datasource.password", pass);
                    }
                }

                environment.getPropertySources().addFirst(new MapPropertySource("neonDatasource", props));
            } catch (Exception ignored) {
                // If parsing fails, do not mutate environment; Spring will raise a clear error later.
            }
        }
    }

    private static String extractUser(URI uri) {
        String userInfo = uri.getUserInfo();
        if (!StringUtils.hasText(userInfo)) return null;
        String[] parts = userInfo.split(":", 2);
        return urlDecode(parts[0]);
    }

    private static String extractPassword(URI uri) {
        String userInfo = uri.getUserInfo();
        if (!StringUtils.hasText(userInfo)) return null;
        String[] parts = userInfo.split(":", 2);
        if (parts.length < 2) return null;
        return urlDecode(parts[1]);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        // Run early so DataSourceAutoConfiguration sees the normalized properties.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

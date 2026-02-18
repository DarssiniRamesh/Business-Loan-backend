package com.example.BusinessLoanAPISpringBoot.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ensures {@code spring.datasource.url} is set from Neon-style environment variables early enough
 * for Flyway/JPA initialization.
 *
 * <p>Supported env vars:</p>
 * <ul>
 *   <li>{@code DATABASE_URL} (preferred)</li>
 *   <li>{@code NEON_DATABASE_URL} (common in preview environments)</li>
 * </ul>
 *
 * <p>Supported formats:</p>
 * <ul>
 *   <li>JDBC: {@code jdbc:postgresql://host:5432/db?sslmode=require&user=u&password=p}</li>
 *   <li>URI: {@code postgresql://user:pass@host/db?sslmode=require}</li>
 * </ul>
 *
 * <p>If {@code spring.datasource.url} is already set, this processor does nothing.</p>
 */
public final class NeonDatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROP_DATASOURCE_URL = "spring.datasource.url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // If user already provided spring.datasource.url explicitly, do not override it.
        String existingJdbc = environment.getProperty(PROP_DATASOURCE_URL);
        if (StringUtils.hasText(existingJdbc)) {
            return;
        }

        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("NEON_DATABASE_URL")
        );

        if (!StringUtils.hasText(raw)) {
            return;
        }

        String jdbc = normalizeToJdbcPostgres(raw.trim());
        if (!StringUtils.hasText(jdbc)) {
            return;
        }

        Map<String, Object> props = Map.of(PROP_DATASOURCE_URL, jdbc);
        environment.getPropertySources().addFirst(new MapPropertySource("neonDatasource", props));
    }

    @Override
    public int getOrder() {
        // Run early so DataSourceAutoConfiguration sees the normalized properties.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Converts a postgres URI (postgresql:// or postgres://) to a JDBC URL.
     * If the input is already a JDBC URL, it is returned unchanged.
     */
    private static String normalizeToJdbcPostgres(String raw) {
        if (raw.startsWith("jdbc:")) {
            return raw;
        }
        if (!(raw.startsWith("postgresql://") || raw.startsWith("postgres://"))) {
            // Unknown format; don't guess.
            return null;
        }

        try {
            URI uri = new URI(raw);

            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return null;
            }

            int port = uri.getPort(); // -1 if absent
            String portPart = (port > 0) ? (":" + port) : "";

            String path = uri.getPath();
            String db = (path == null) ? "" : path.replaceFirst("^/", "");
            if (!StringUtils.hasText(db)) {
                return null;
            }

            // Parse existing query parameters (keep order stable).
            Map<String, String> params = new LinkedHashMap<>();
            String query = uri.getRawQuery();
            if (StringUtils.hasText(query)) {
                for (String pair : query.split("&")) {
                    if (!StringUtils.hasText(pair)) continue;
                    String[] kv = pair.split("=", 2);
                    String k = urlDecode(kv[0]);
                    String v = kv.length > 1 ? urlDecode(kv[1]) : "";
                    if (StringUtils.hasText(k)) {
                        params.putIfAbsent(k, v);
                    }
                }
            }

            // Neon URI commonly embeds username/password in userinfo; JDBC supports them as query params.
            String userInfo = uri.getRawUserInfo();
            if (StringUtils.hasText(userInfo)) {
                String[] up = userInfo.split(":", 2);
                String user = urlDecode(up[0]);
                if (StringUtils.hasText(user)) {
                    params.putIfAbsent("user", user);
                }
                if (up.length > 1) {
                    String pass = urlDecode(up[1]);
                    if (StringUtils.hasText(pass)) {
                        params.putIfAbsent("password", pass);
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("jdbc:postgresql://").append(host).append(portPart).append("/").append(db);

            if (!params.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (!first) sb.append("&");
                    first = false;
                    sb.append(urlEncode(e.getKey())).append("=").append(urlEncode(e.getValue()));
                }
            }

            return sb.toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String s) {
        // Minimal encoder for query pairs; keep it simple and safe.
        // Space to %20, plus to %2B, etc.
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

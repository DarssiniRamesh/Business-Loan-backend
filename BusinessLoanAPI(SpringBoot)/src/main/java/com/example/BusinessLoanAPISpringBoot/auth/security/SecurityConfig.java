package com.example.BusinessLoanAPISpringBoot.auth.security;

import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security configuration: stateless JWT auth.
 *
 * Method security is enabled so controllers can use @PreAuthorize for RBAC.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * CORS configuration for browser-based clients (React preview, local dev, etc).
     *
     * Why this is needed:
     * - The frontend preview runs on a different origin (often same host but different port),
     *   so browsers will send a preflight OPTIONS request before POST/PUT/PATCH/DELETE.
     * - Without an explicit CorsConfigurationSource, Spring Security may not emit
     *   Access-Control-Allow-* headers, causing the browser to surface a generic "Network Error".
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(Environment env) {
        // Comma-separated list of allowed origin patterns.
        // Keep this configurable; defaults cover local dev and Kavia preview hosts.
        String patternsProp = env.getProperty(
                "app.cors.allowed-origin-patterns",
                String.join(",",
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://localhost:*",
                        "https://127.0.0.1:*",
                        // Kavia preview hosts commonly look like:
                        //   https://vscode-internal-<id>-beta.beta01.cloud.kavia.ai(:port)
                        "https://*.cloud.kavia.ai",
                        "https://*.cloud.kavia.ai:*"
                )
        );

        List<String> allowedOriginPatterns = Arrays.stream(patternsProp.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);

        // Allow credentials to support cookie-based flows if the frontend enables them.
        // (If not used, browsers simply won't send cookies.)
        config.setAllowCredentials(true);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-XSRF-TOKEN"
        ));
        config.setExposedHeaders(List.of(
                "Location"
        ));

        // Cache preflight for 1 hour.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply to all endpoints (including /api/auth/register).
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                // Uses the CorsConfigurationSource bean above.
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Ensure correct HTTP semantics:
                // - 401 when authentication is missing/invalid (e.g., missing/invalid JWT)
                // - 403 when authenticated user lacks required roles/permissions
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new AccessDeniedHandlerImpl())
                )
                .authorizeHttpRequests(registry -> registry
                        // CORS preflight must always be allowed, otherwise browsers surface "Network Error"
                        // before the actual request is even sent.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // public
                        .requestMatchers("/", "/health", "/docs", "/api/info").permitAll()

                        // Swagger/OpenAPI (springdoc)
                        // NOTE: "/v3/api-docs/**" does NOT match the exact "/v3/api-docs" path,
                        // which causes a 403 when Swagger UI tries to load the OpenAPI JSON.
                        .requestMatchers(
                                // Swagger UI
                                "/swagger-ui.html",
                                "/swagger-ui/**",

                                // OpenAPI endpoints (springdoc)
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/**",

                                // Explicit swagger-config allow (Swagger UI loads this first)
                                "/v3/api-docs/swagger-config",

                                // If a reverse proxy forwards the preview base path through to the app
                                // (instead of stripping it), also allow proxied Swagger/OpenAPI endpoints.
                                //
                                // IMPORTANT:
                                // Spring Security 6 uses Spring MVC's PathPatternParser for these String patterns.
                                // Patterns like "/proxy/**/v3/api-docs/**" are invalid because "**" cannot appear
                                // in the middle of the pattern with additional path segments following it.
                                // In our preview/proxy setup the path shape is typically:
                                //   /proxy/{port-or-appId}/...
                                // so a single-segment wildcard is correct and avoids PatternParseException.
                                "/proxy/*/swagger-ui.html",
                                "/proxy/*/swagger-ui/**",
                                "/proxy/*/v3/api-docs",
                                "/proxy/*/v3/api-docs.yaml",
                                "/proxy/*/v3/api-docs/**",
                                "/proxy/*/v3/api-docs/swagger-config",

                                // Compatibility allowlist:
                                // When Swagger UI is mounted under /swagger-ui, some configurations/proxies can lead to a
                                // mistaken fetch of:
                                //   /swagger-ui/v3/api-docs/swagger-config
                                // (Observed in preview behind /proxy/3010)
                                "/swagger-ui/v3/api-docs/**",

                                "/api-docs/**" // legacy/custom path if enabled in some environments
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()

                        // everything else requires auth
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

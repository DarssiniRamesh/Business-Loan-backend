package com.example.BusinessLoanAPISpringBoot.auth.security;

import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Spring Security configuration: stateless JWT auth.
 *
 * Method security is enabled so controllers can use @PreAuthorize for RBAC.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
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

package com.example.BusinessLoanAPISpringBoot.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Authentication module configuration.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {}

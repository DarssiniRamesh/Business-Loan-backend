package com.example.BusinessLoanAPISpringBoot;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entrypoint for the Business Loan API Spring Boot application.
 */
@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "Business Loan API",
                version = "0.1.0",
                description = "Backend APIs for authentication (JWT+MFA) and loan application workflows, backed by Neon PostgreSQL."
        )
)
@Tag(name = "Auth", description = "Authentication and MFA endpoints")
@Tag(name = "System", description = "System/health endpoints")
public class BusinessLoanAPISpringBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessLoanAPISpringBootApplication.class, args);
    }
}

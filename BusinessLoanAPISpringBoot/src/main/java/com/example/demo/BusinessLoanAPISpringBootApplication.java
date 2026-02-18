package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application entrypoint.
 *
 * <p>Note: package is intentionally {@code com.example.demo} to match the source folder
 * structure and ensure Spring component scanning discovers controllers, services,
 * entities, and repositories under this base package.</p>
 */
@SpringBootApplication
public class BusinessLoanAPISpringBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(BusinessLoanAPISpringBootApplication.class, args);
	}

}

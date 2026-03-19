package com.example.BusinessLoanAPISpringBoot.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeonDatasourceEnvironmentPostProcessorTest {

    private final NeonDatasourceEnvironmentPostProcessor processor = new NeonDatasourceEnvironmentPostProcessor();
    private final MockEnvironment env = new MockEnvironment();

    @Test
    void convertsNeonPostgresUrlToJdbc() {
        env.setProperty("DATABASE_URL", "postgres://user:pass@ep-cool-frog-123456.us-east-2.aws.neon.tech/neondb");

        processor.postProcessEnvironment(env, new SpringApplication());

        String url = env.getProperty("spring.datasource.url");
        assertEquals("jdbc:postgresql://ep-cool-frog-123456.us-east-2.aws.neon.tech/neondb?user=user&password=pass", url);
    }

    @Test
    void prefersExistingJdbcUrl() {
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/mydb");
        env.setProperty("DATABASE_URL", "postgres://ignored");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("jdbc:postgresql://localhost:5432/mydb", env.getProperty("spring.datasource.url"));
    }

    @Test
    void handlesSslMode() {
        env.setProperty("DATABASE_URL", "postgresql://user:pass@host/db?sslmode=require");

        processor.postProcessEnvironment(env, new SpringApplication());

        String url = env.getProperty("spring.datasource.url");
        // Ensure parameters are preserved and formatted correctly
        assertEquals("jdbc:postgresql://host/db?sslmode=require&user=user&password=pass", url);
    }
}

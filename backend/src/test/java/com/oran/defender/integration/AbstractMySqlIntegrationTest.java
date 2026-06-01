package com.oran.defender.integration;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for tests that need a real database. Starts a single MySQL 8 container once for the whole
 * JVM (the static initialiser runs once and the container is deliberately never stopped, so every
 * integration test class shares the one instance — Testcontainers' Ryuk reaps it when the JVM
 * exits). One shared container is far faster than one per class, and testing against MySQL — the
 * engine we run in production — avoids the dialect drift an in-memory DB would hide.
 *
 * <p>Subclasses add their own {@code @SpringBootTest} (MOCK for slice/service tests, RANDOM_PORT
 * for the HTTP system test); the inherited container wiring and active profile apply either way.
 */
@ActiveProfiles("test")
public abstract class AbstractMySqlIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}

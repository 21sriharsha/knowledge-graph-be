package com.knowledge.platform;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need a real PostgreSQL with pgvector.
 *
 * <p>A real database rather than an in-memory substitute, because most of what is worth testing here
 * cannot exist without one: a generated {@code tsvector} column, {@code ts_rank_cd}, pgvector's
 * {@code <=>} operator, partial and expression indexes, and Liquibase's migrations themselves. An H2
 * or Postgres-flavoured emulation would pass while the production schema failed.
 *
 * <p>The container is static and shared across every subclass, so the image starts once for the whole
 * suite rather than once per test class.
 *
 * <p>Subclasses carry {@code @EnabledIf(...containerRuntimeAvailable)} themselves rather than
 * inheriting it. JUnit evaluates execution conditions against the class being run, and neither a
 * subclass nor a {@code @Nested} class reliably picks the annotation up from here -- which showed up
 * as the whole suite erroring on a machine with no container runtime instead of skipping.
 *
 * <p><b>Skipped rather than failing when no container runtime is available.</b> The build must be
 * runnable on a machine without one; these tests then do not run, and the unit tests still do. With
 * rootless Podman, start the socket and point Testcontainers at it:
 *
 * <pre>
 *   systemctl --user start podman.socket
 *   export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationtest")
public abstract class AbstractPostgresIntegrationTest {

    /**
     * pgvector rather than the stock Postgres image: the first migration creates the {@code vector}
     * extension, so the base image would fail before any table exists.
     */
    private static final DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @SuppressWarnings("resource") // Closed by the JVM shutdown hook; see the Ryuk note in testcontainers.properties.
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("knowledge")
            .withUsername("knowledge")
            .withPassword("knowledge")
            .withReuse(false);

    static {
        if (containerRuntimeAvailable()) {
            POSTGRES.start();
        }
    }

    /** Probed once and cached: a failed probe is slow, and every test class would otherwise repeat it. */
    private static Boolean runtimeAvailable;

    public static synchronized boolean containerRuntimeAvailable() {
        if (runtimeAvailable == null) {
            try {
                runtimeAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
            } catch (RuntimeException e) {
                runtimeAvailable = false;
            }
        }
        return runtimeAvailable;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

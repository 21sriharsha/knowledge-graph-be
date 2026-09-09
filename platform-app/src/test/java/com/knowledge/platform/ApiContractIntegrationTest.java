package com.knowledge.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract: which endpoints are public, which require authentication, and what an error
 * looks like.
 *
 * <p>Security is asserted from the outside because the frontend is not a security boundary — the
 * guarantee is about what the HTTP surface does, not about which annotations are present. The studio
 * zone in particular is protected by its path prefix, and this is what proves a new endpoint under
 * that prefix is protected by default rather than by somebody remembering to annotate it.
 */
@EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
        disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
@AutoConfigureMockMvc
class ApiContractIntegrationTest extends AbstractPostgresIntegrationTest {

    /** Matches the credentials in application-integrationtest.yaml. */
    private static final String AUTHOR_USER = "test-author";
    private static final String AUTHOR_PASSWORD = "test-password";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("public read zone")
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest"
            + "#containerRuntimeAvailable", disabledReason = "No container runtime")
    class PublicZone {

        @ParameterizedTest
        @DisplayName("read endpoints are anonymous")
        @ValueSource(strings = {
                "/api/search?q=anything",
                "/api/navigation",
                "/api/topics",
                "/api/tags",
                "/api/articles",
                "/api/authors"
        })
        void servesReadEndpointsAnonymously(String path) throws Exception {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("an unknown slug is 404 with the ApiError shape, not a container error page")
        void returnsTheErrorContractForUnknownContent() throws Exception {
            mockMvc.perform(get("/api/articles/no-such-article"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.path").value("/api/articles/no-such-article"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("an unpublished article is indistinguishable from one that does not exist")
        void doesNotConfirmUnpublishedSlugs() throws Exception {
            // A 403 here would confirm the slug to anyone guessing.
            mockMvc.perform(get("/api/articles/some-draft"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void rejectsAnOversizedQueryWithTheErrorContract() throws Exception {
            mockMvc.perform(get("/api/search").param("q", "x".repeat(1000)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("graph depth is bounded at the API, before any traversal starts")
        void boundsGraphDepth() throws Exception {
            mockMvc.perform(get("/api/graph").param("slug", "anything").param("depth", "99"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("studio zone")
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest"
            + "#containerRuntimeAvailable", disabledReason = "No container runtime")
    class StudioZone {

        @ParameterizedTest
        @DisplayName("every studio endpoint refuses an anonymous request")
        @ValueSource(strings = {
                "/api/studio/sources",
                "/api/studio/articles",
                "/api/studio/ingestions",
                "/api/studio/ingestions/pipeline"
        })
        void requiresAuthentication(String path) throws Exception {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }

        @Test
        void acceptsValidCredentials() throws Exception {
            mockMvc.perform(get("/api/studio/sources")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors
                                    .httpBasic(AUTHOR_USER, AUTHOR_PASSWORD)))
                    .andExpect(status().isOk());
        }

        @Test
        void rejectsWrongCredentials() throws Exception {
            mockMvc.perform(get("/api/studio/sources")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors
                                    .httpBasic(AUTHOR_USER, "wrong")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a mutation is refused anonymously, not just a read")
        void protectsWrites() throws Exception {
            mockMvc.perform(post("/api/studio/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an invalid payload reports field violations rather than a stack trace")
        void reportsValidationViolations() throws Exception {
            mockMvc.perform(post("/api/studio/sources")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors
                                    .httpBasic(AUTHOR_USER, AUTHOR_PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceType\":\"GITHUB\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations").isArray())
                    .andExpect(jsonPath("$.violations[0].field").exists());
        }
    }

    @Nested
    @DisplayName("operational endpoints")
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest"
            + "#containerRuntimeAvailable", disabledReason = "No container runtime")
    class Operational {

        @Test
        void healthIsPublic() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("metrics expose traffic patterns, so they need the ADMIN role")
        void metricsRequireAdmin() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        }

        @Test
        void openApiDocumentIsPublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/search']").exists())
                    .andExpect(jsonPath("$.paths['/api/articles/{slug}']").exists());
        }

        @Test
        @DisplayName("the AI indicator reports the deterministic configuration rather than failing")
        void reportsAiHealth() throws Exception {
            mockMvc.perform(get("/actuator/health")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors
                                    .httpBasic(AUTHOR_USER, AUTHOR_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.ai.details.enabled").value(false))
                    .andExpect(jsonPath("$.components.ai.details.mode").value("deterministic"));
        }
    }
}

package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowledge.platform.source.integration.adapter.github.GitHubSourceAdapterImpl;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceService;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The webhook endpoint end to end: HTTP, signature verification, and the delivery ledger.
 *
 * <p>The adapter is a <em>spy</em>, not a mock. Signature verification and payload parsing are the
 * behaviour under test, so they run for real; only the methods that would make network calls are
 * stubbed, so the ingestion job the webhook triggers does not try to reach GitHub.
 *
 * <p>Response codes are asserted deliberately, because providers act on them: anything other than a
 * 2xx makes them retry, so a verified event the platform does not act on must still answer 200 or a
 * ping ends up retried forever.
 */
@EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
        disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
@AutoConfigureMockMvc
class WebhookIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SECRET = "webhook-signing-secret";

    @MockitoSpyBean
    private GitHubSourceAdapterImpl gitHubAdapter;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SourceRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                truncate table read_model.article_read_models, read_model.routes,
                               search.article_embeddings, search.article_search_documents,
                               graph.suggested_relationships, graph.article_links,
                               content.article_topics, content.article_tags,
                               content.article_revisions, content.articles,
                               content.topics, content.tags,
                               ingestion.events, ingestion.runs,
                               source.webhook_deliveries, source.repositories,
                               author.authors
                restart identity cascade
                """);

        repository = sourceService.connect(SourceType.GITHUB, "Handbook", "acme", "handbook",
                null, "main", "docs", null, "token", SECRET, null, null);

        // Only the network-touching methods are stubbed; verification and parsing stay real.
        doReturn(Optional.of("rev-1")).when(gitHubAdapter).resolveHeadRevision(any(), anyString());
        doReturn(List.of()).when(gitHubAdapter).listTree(any(), anyString(), anyString());
        doReturn(Optional.empty()).when(gitHubAdapter).readFile(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a correctly signed push is accepted")
    void acceptsASignedPush() throws Exception {
        String body = pushPayload("delivery-1");

        mockMvc.perform(webhook(body, "push", "delivery-1", sign(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @DisplayName("an unsigned request is rejected; the endpoint has no other authentication")
    void rejectsAnUnsignedRequest() throws Exception {
        String body = pushPayload("delivery-2");

        mockMvc.perform(post("/api/webhooks/sources/{id}", repository.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnIncorrectSignature() throws Exception {
        String body = pushPayload("delivery-3");

        mockMvc.perform(webhook(body, "push", "delivery-3", "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("rejected"));
    }

    @Test
    @DisplayName("a signature over different bytes is rejected, so a replayed body cannot reuse one")
    void rejectsASignatureForDifferentContent() throws Exception {
        String body = pushPayload("delivery-4");
        String signatureForSomethingElse = sign(pushPayload("delivery-other"));

        mockMvc.perform(webhook(body, "push", "delivery-4", signatureForSomethingElse))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the failure reason is not disclosed to an unverified caller")
    void doesNotExplainWhyVerificationFailed() throws Exception {
        String body = pushPayload("delivery-5");

        mockMvc.perform(webhook(body, "push", "delivery-5", "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @DisplayName("a verified ping is answered 200, or the provider retries it forever")
    void acknowledgesEventsItDoesNotActOn() throws Exception {
        String body = "{\"zen\":\"Keep it logically awesome.\"}";

        mockMvc.perform(webhook(body, "ping", "delivery-6", sign(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
    }

    @Test
    @DisplayName("a push to another branch is verified but not acted on")
    void ignoresPushesToOtherBranches() throws Exception {
        String body = pushPayload("delivery-7").replace("refs/heads/main", "refs/heads/experiment");

        mockMvc.perform(webhook(body, "push", "delivery-7", sign(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
    }

    @Test
    @DisplayName("a redelivered webhook is recorded once and not reprocessed")
    void isIdempotentAcrossRedeliveries() throws Exception {
        String body = pushPayload("delivery-8");

        mockMvc.perform(webhook(body, "push", "delivery-8", sign(body)))
                .andExpect(status().isAccepted());
        // Providers retry deliveries they believe failed. Without the ledger this would re-run the
        // whole materialization pipeline.
        mockMvc.perform(webhook(body, "push", "delivery-8", sign(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));

        Integer recorded = jdbcTemplate.queryForObject(
                "select count(*) from source.webhook_deliveries where delivery_id = 'delivery-8'",
                Integer.class);
        assertThat(recorded).isEqualTo(1);
    }

    @Test
    @DisplayName("a deactivated repository stops accepting webhooks")
    void ignoresWebhooksForAnInactiveRepository() throws Exception {
        sourceService.setActive(repository.getId(), false);
        String body = pushPayload("delivery-9");

        mockMvc.perform(webhook(body, "push", "delivery-9", sign(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
    }

    @Test
    @DisplayName("an unknown repository id is a 404, not a 500")
    void reportsAnUnknownRepositoryCleanly() throws Exception {
        String body = pushPayload("delivery-10");

        mockMvc.perform(post("/api/webhooks/sources/{id}", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .header("X-GitHub-Delivery", "delivery-10")
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("a malformed body cannot pass verification, so it never reaches the parser")
    void rejectsGarbageBeforeParsingIt() throws Exception {
        mockMvc.perform(webhook("not json at all", "push", "delivery-11", "sha256=deadbeef"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(
            String body, String event, String delivery, String signature) {
        return post("/api/webhooks/sources/{id}", repository.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", event)
                .header("X-GitHub-Delivery", delivery)
                .header("X-Hub-Signature-256", signature)
                .content(body);
    }

    private String pushPayload(String deliveryId) {
        return """
                {
                  "ref": "refs/heads/main",
                  "after": "rev-1",
                  "head_commit": {"id": "rev-1"},
                  "commits": [
                    {"id": "%s", "added": ["docs/a.md"], "modified": [], "removed": []}
                  ]
                }
                """.formatted(deliveryId);
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of()
                    .formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

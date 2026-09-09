package com.knowledge.platform.source.integration.adapter;

import com.knowledge.platform.source.integration.adapter.github.GitHubSourceAdapterImpl;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

/**
 * GitHub against the shared adapter contract.
 *
 * <p>The fixtures reproduce the provider's real quirks rather than an idealized shape: base64 content
 * wrapped at 60 characters, a flat recursive tree with {@code blob}/{@code tree} types, and per-commit
 * added/modified/removed path lists that have to be flattened across commits.
 */
class GitHubSourceAdapterContractTest extends SourceAdapterContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected SourceAdapter adapter(SourceProperties properties) {
        return new GitHubSourceAdapterImpl(properties, MAPPER);
    }

    @Override
    protected SourceType expectedType() {
        return SourceType.GITHUB;
    }

    @Override
    protected boolean reportsChangedPaths() {
        return true;
    }

    @Override
    protected void registerRoutes() {
        route("/repos/" + OWNER + "/" + REPOSITORY + "/git/trees", exchange -> respond(exchange, 200, """
                {
                  "sha": "%s",
                  "truncated": false,
                  "tree": [
                    {"path": "docs",                "type": "tree", "sha": "t1"},
                    {"path": "docs/onboarding.md",  "type": "blob", "sha": "b1", "size": 96},
                    {"path": "docs/deployment.md",  "type": "blob", "sha": "b2", "size": 40},
                    {"path": "docs/logo.png",       "type": "blob", "sha": "b3", "size": 2048}
                  ]
                }
                """.formatted(REVISION)));

        route("/repos/" + OWNER + "/" + REPOSITORY + "/contents/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (!path.endsWith("docs/onboarding.md")) {
                respond(exchange, 404, "{\"message\":\"Not Found\"}");
                return;
            }
            // GitHub wraps base64 at 60 characters; the standard decoder rejects those newlines,
            // which is exactly the quirk the adapter has to absorb.
            String wrapped = Base64.getMimeEncoder(60, "\n".getBytes(StandardCharsets.UTF_8))
                    .encodeToString(MARKDOWN.getBytes(StandardCharsets.UTF_8));
            respond(exchange, 200, MAPPER.writeValueAsString(Map.of(
                    "path", "docs/onboarding.md",
                    "sha", "b1",
                    "size", MARKDOWN.length(),
                    "encoding", "base64",
                    "content", wrapped)));
        });

        route("/repos/" + OWNER + "/" + REPOSITORY + "/commits/",
                exchange -> respond(exchange, 200, "{\"sha\":\"" + REVISION + "\"}"));
    }

    @Override
    protected WebhookRequest validPushWebhook() {
        String body = pushPayload();
        return new WebhookRequest(Map.of(
                "X-GitHub-Event", "push",
                "X-GitHub-Delivery", "delivery-1",
                "X-Hub-Signature-256", sign(body)), body);
    }

    @Override
    protected WebhookRequest tamperedPushWebhook() {
        String body = pushPayload();
        // The signature is computed over different bytes than the ones delivered -- the shape of a
        // replayed body with a stolen signature.
        return new WebhookRequest(Map.of(
                "X-GitHub-Event", "push",
                "X-GitHub-Delivery", "delivery-1",
                "X-Hub-Signature-256", sign(body + " ")), body);
    }

    @Override
    protected WebhookRequest nonPushWebhook() {
        String body = "{\"zen\":\"Keep it logically awesome.\"}";
        return new WebhookRequest(Map.of(
                "X-GitHub-Event", "ping",
                "X-Hub-Signature-256", sign(body)), body);
    }

    private String pushPayload() {
        return """
                {
                  "ref": "refs/heads/%s",
                  "after": "%s",
                  "head_commit": {"id": "%s"},
                  "commits": [
                    {"id": "c1", "added": ["docs/onboarding.md"], "modified": [], "removed": []},
                    {"id": "c2", "added": [], "modified": ["docs/deployment.md"],
                     "removed": ["docs/legacy.md"]}
                  ]
                }
                """.formatted(BRANCH, REVISION, REVISION);
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of()
                    .formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

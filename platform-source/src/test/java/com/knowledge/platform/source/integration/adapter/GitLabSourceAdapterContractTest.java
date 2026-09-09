package com.knowledge.platform.source.integration.adapter;

import com.knowledge.platform.source.integration.adapter.gitlab.GitLabSourceAdapterImpl;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * GitLab against the shared adapter contract.
 *
 * <p>The fixtures reproduce GitLab's own quirks: a project addressed by URL-encoded
 * {@code owner/repo}, a paginated tree, and a plain shared token as the webhook credential rather
 * than a MAC over the body.
 */
class GitLabSourceAdapterContractTest extends SourceAdapterContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected SourceAdapter adapter(SourceProperties properties) {
        return new GitLabSourceAdapterImpl(properties, MAPPER);
    }

    @Override
    protected SourceType expectedType() {
        return SourceType.GITLAB;
    }

    @Override
    protected boolean reportsChangedPaths() {
        return true;
    }

    @Override
    protected void registerRoutes() {
        route("/api/v4/projects", exchange -> {
            String path = exchange.getRequestURI().getPath();

            if (path.endsWith("/repository/tree")) {
                // Page 2 is empty, which is how the adapter learns the listing has ended.
                String page = queryParam(exchange, "page");
                if (page != null && !page.equals("1")) {
                    respond(exchange, 200, "[]");
                    return;
                }
                respond(exchange, 200, """
                        [
                          {"id": "t1", "name": "docs",          "type": "tree", "path": "docs"},
                          {"id": "b1", "name": "onboarding.md", "type": "blob", "path": "docs/onboarding.md"},
                          {"id": "b2", "name": "deployment.md", "type": "blob", "path": "docs/deployment.md"},
                          {"id": "b3", "name": "logo.png",      "type": "blob", "path": "docs/logo.png"}
                        ]
                        """);
                return;
            }

            if (path.contains("/repository/files/")) {
                if (!path.contains("onboarding")) {
                    respond(exchange, 404, "{\"message\":\"404 File Not Found\"}");
                    return;
                }
                respond(exchange, 200, MAPPER.writeValueAsString(Map.of(
                        "file_path", "docs/onboarding.md",
                        "blob_id", "b1",
                        "size", MARKDOWN.length(),
                        "encoding", "base64",
                        "last_commit_id", REVISION,
                        "content", Base64.getEncoder()
                                .encodeToString(MARKDOWN.getBytes(StandardCharsets.UTF_8)))));
                return;
            }

            if (path.contains("/repository/commits/")) {
                respond(exchange, 200, "{\"id\":\"" + REVISION + "\"}");
                return;
            }
            respond(exchange, 404, "{\"message\":\"Not Found\"}");
        });
    }

    @Override
    protected WebhookRequest validPushWebhook() {
        return new WebhookRequest(Map.of(
                "X-Gitlab-Event", "Push Hook",
                "X-Gitlab-Event-UUID", "delivery-1",
                "X-Gitlab-Token", WEBHOOK_SECRET), pushPayload());
    }

    @Override
    protected WebhookRequest tamperedPushWebhook() {
        return new WebhookRequest(Map.of(
                "X-Gitlab-Event", "Push Hook",
                "X-Gitlab-Event-UUID", "delivery-1",
                "X-Gitlab-Token", "wrong-token"), pushPayload());
    }

    @Override
    protected WebhookRequest nonPushWebhook() {
        return new WebhookRequest(Map.of(
                "X-Gitlab-Event", "Issue Hook",
                "X-Gitlab-Token", WEBHOOK_SECRET), "{\"object_kind\":\"issue\"}");
    }

    private String pushPayload() {
        return """
                {
                  "ref": "refs/heads/%s",
                  "checkout_sha": "%s",
                  "commits": [
                    {"id": "c1", "added": ["docs/onboarding.md"], "modified": [], "removed": []},
                    {"id": "c2", "added": [], "modified": ["docs/deployment.md"],
                     "removed": ["docs/legacy.md"]}
                  ]
                }
                """.formatted(BRANCH, REVISION);
    }
}

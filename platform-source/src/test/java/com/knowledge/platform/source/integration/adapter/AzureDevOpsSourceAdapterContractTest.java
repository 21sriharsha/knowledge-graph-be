package com.knowledge.platform.source.integration.adapter;

import com.knowledge.platform.source.integration.adapter.azuredevops.AzureDevOpsSourceAdapterImpl;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Azure DevOps against the shared adapter contract.
 *
 * <p>This provider is the reason the contract is worth having. Its wire format differs from the other
 * two in every particular -- {@code {count, value}} envelopes, absolute repository paths, HTTP Basic
 * with an empty username, plain-text content, and push payloads that name no files at all -- and the
 * contract asserts that the same normalized types come out regardless.
 */
class AzureDevOpsSourceAdapterContractTest extends SourceAdapterContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected SourceAdapter adapter(SourceProperties properties) {
        return new AzureDevOpsSourceAdapterImpl(properties, MAPPER);
    }

    @Override
    protected SourceType expectedType() {
        return SourceType.AZURE_DEVOPS;
    }

    /** Azure DevOps service hooks report commits but never the paths they touched. */
    @Override
    protected boolean reportsChangedPaths() {
        return false;
    }

    @Override
    protected void registerRoutes() {
        String prefix = "/" + OWNER + "/" + PROJECT + "/_apis/git/repositories/" + REPOSITORY;

        route(prefix + "/items", exchange -> {
            String requestedPath = queryParam(exchange, "path");

            if (requestedPath == null) {
                // Tree listing. Paths are absolute here; the adapter has to strip the leading slash
                // to match every other provider's repository-relative form.
                respond(exchange, 200, """
                        {
                          "count": 4,
                          "value": [
                            {"objectId": "t1", "gitObjectType": "tree", "path": "/docs", "isFolder": true},
                            {"objectId": "b1", "gitObjectType": "blob", "path": "/docs/onboarding.md"},
                            {"objectId": "b2", "gitObjectType": "blob", "path": "/docs/deployment.md"},
                            {"objectId": "b3", "gitObjectType": "blob", "path": "/docs/logo.png"}
                          ]
                        }
                        """);
                return;
            }

            if (!requestedPath.endsWith("onboarding.md")) {
                respond(exchange, 404, "{\"message\":\"TF401174: item not found\"}");
                return;
            }
            // With $format=json the content comes back as plain text, not base64.
            respond(exchange, 200, MAPPER.writeValueAsString(Map.of(
                    "objectId", "b1",
                    "gitObjectType", "blob",
                    "path", "/docs/onboarding.md",
                    "content", MARKDOWN)));
        });

        route(prefix + "/commits", exchange -> respond(exchange, 200, """
                {"count": 1, "value": [{"commitId": "%s"}]}
                """.formatted(REVISION)));
    }

    @Override
    protected WebhookRequest validPushWebhook() {
        return new WebhookRequest(Map.of("Authorization", basicAuth(WEBHOOK_SECRET)), pushPayload());
    }

    @Override
    protected WebhookRequest tamperedPushWebhook() {
        return new WebhookRequest(Map.of("Authorization", basicAuth("wrong-secret")), pushPayload());
    }

    @Override
    protected WebhookRequest nonPushWebhook() {
        return new WebhookRequest(Map.of("Authorization", basicAuth(WEBHOOK_SECRET)),
                "{\"id\":\"e1\",\"eventType\":\"git.pullrequest.created\",\"resource\":{}}");
    }

    private String pushPayload() {
        return """
                {
                  "id": "delivery-1",
                  "eventType": "git.push",
                  "resource": {
                    "commits": [{"commitId": "%s"}],
                    "refUpdates": [
                      {"name": "refs/heads/%s", "newObjectId": "%s", "oldObjectId": "old"}
                    ]
                  }
                }
                """.formatted(REVISION, BRANCH, REVISION);
    }

    private String basicAuth(String secret) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (":" + secret).getBytes(StandardCharsets.UTF_8));
    }
}

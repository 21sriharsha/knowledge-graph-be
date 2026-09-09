package com.knowledge.platform.source.integration.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledge.platform.source.model.dto.PushNotification;
import com.knowledge.platform.source.model.dto.RemoteEntry;
import com.knowledge.platform.source.model.dto.RepositoryDescriptor;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The contract every source adapter must satisfy, regardless of provider.
 *
 * <p>This is the test the specification asks for when it says adapters should be tested against
 * normalized provider contracts. The point is not that each adapter works in isolation, but that all
 * three produce <em>the same normalized types from wildly different wire formats</em> -- GitHub's
 * line-wrapped base64, GitLab's paginated arrays, Azure DevOps' {@code {count, value}} envelopes and
 * absolute paths. If they did not, the promise that the same content is ingestible regardless of
 * provider would be untrue, and ingestion would eventually grow provider-specific branches.
 *
 * <p>A real HTTP server backs the tests rather than a mocked client, so URL construction, encoding,
 * headers, authentication and status handling are all genuinely exercised. Subclasses supply the
 * provider's wire fixtures; the assertions live here and apply to all of them.
 */
abstract class SourceAdapterContractTest {

    protected static final String OWNER = "acme";
    protected static final String REPOSITORY = "handbook";
    protected static final String PROJECT = "docs";
    protected static final String BRANCH = "main";
    protected static final String REVISION = "abc123def456";
    protected static final String WEBHOOK_SECRET = "s3cr3t-webhook-key";
    protected static final String MARKDOWN = "# Onboarding\n\nSee [[Deployment]] for the next step.\n";

    private HttpServer server;
    protected String baseUrl;
    private final Map<String, Handler> routes = new HashMap<>();

    /** The adapter under test, built against {@code baseUrl}. */
    protected abstract SourceAdapter adapter(SourceProperties properties);

    /** Registers the provider's endpoints on the stub server. */
    protected abstract void registerRoutes();

    /** A webhook request the adapter should accept as authentic. */
    protected abstract WebhookRequest validPushWebhook();

    /** The same webhook with its authentication broken. */
    protected abstract WebhookRequest tamperedPushWebhook();

    /** A verified event this platform does not act on -- a ping, a tag push. */
    protected abstract WebhookRequest nonPushWebhook();

    protected abstract SourceType expectedType();

    /** Whether this provider reports which files a push touched. */
    protected abstract boolean reportsChangedPaths();

    @BeforeEach
    void startStubProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Handler handler = routes.entrySet().stream()
                    .filter(entry -> path.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (handler == null) {
                respond(exchange, 404, "{\"message\":\"Not Found\"}");
                return;
            }
            handler.handle(exchange);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        routes.clear();
        registerRoutes();
    }

    @AfterEach
    void stopStubProvider() {
        server.stop(0);
    }

    @Test
    @DisplayName("reports its provider, so the factory can bind it to a strategy")
    void declaresItsProvider() {
        assertThat(adapter(properties()).providerType()).isEqualTo(expectedType());
    }

    @Test
    @DisplayName("lists the tree as repository-relative paths, whatever the provider returns")
    void listsTheTreeNormalized() {
        List<RemoteEntry> entries = adapter(properties()).listTree(descriptor(), REVISION, "");

        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.path()).doesNotStartWith("/");
            assertThat(entry.path()).isNotBlank();
        });
        assertThat(entries).extracting(RemoteEntry::path).contains("docs/onboarding.md");
    }

    @Test
    @DisplayName("distinguishes Markdown from other files, so ingestion does not have to")
    void identifiesMarkdownEntries() {
        List<RemoteEntry> entries = adapter(properties()).listTree(descriptor(), REVISION, "");

        assertThat(entries).filteredOn(RemoteEntry::isMarkdown)
                .extracting(RemoteEntry::path)
                .contains("docs/onboarding.md")
                .doesNotContain("docs/logo.png");
    }

    @Test
    @DisplayName("decodes file content to UTF-8, whatever encoding the provider used on the wire")
    void readsFileContentDecoded() {
        Optional<SourceFile> file =
                adapter(properties()).readFile(descriptor(), "docs/onboarding.md", REVISION);

        assertThat(file).isPresent();
        assertThat(file.get().content()).isEqualTo(MARKDOWN);
        assertThat(file.get().path()).isEqualTo("docs/onboarding.md");
        assertThat(file.get().revision()).isEqualTo(REVISION);
    }

    @Test
    @DisplayName("a missing file is empty, not an error -- that is what a deletion looks like")
    void treatsAMissingFileAsAbsent() {
        assertThat(adapter(properties()).readFile(descriptor(), "docs/gone.md", REVISION)).isEmpty();
    }

    @Test
    void resolvesTheHeadRevisionOfABranch() {
        assertThat(adapter(properties()).resolveHeadRevision(descriptor(), BRANCH))
                .contains(REVISION);
    }

    @Test
    @DisplayName("a file over the configured limit is skipped rather than parsed")
    void skipsOversizedFiles() {
        SourceProperties tiny = new SourceProperties(null, Duration.ofSeconds(5), 1, 100);

        assertThat(adapter(tiny).readFile(descriptor(), "docs/onboarding.md", REVISION)).isEmpty();
    }

    @Test
    void acceptsAnAuthenticWebhook() {
        assertThat(adapter(properties()).verifyWebhook(validPushWebhook(), WEBHOOK_SECRET)).isTrue();
    }

    @Test
    @DisplayName("a tampered webhook is rejected -- this check is the only thing guarding the endpoint")
    void rejectsATamperedWebhook() {
        assertThat(adapter(properties()).verifyWebhook(tamperedPushWebhook(), WEBHOOK_SECRET)).isFalse();
    }

    @Test
    void rejectsAWebhookWhenNoSecretIsKnown() {
        assertThat(adapter(properties()).verifyWebhook(validPushWebhook(), null)).isFalse();
    }

    @Test
    @DisplayName("normalizes a push into branch, revision and paths")
    void normalizesAPush() {
        Optional<PushNotification> push = adapter(properties()).parsePush(validPushWebhook());

        assertThat(push).isPresent();
        assertThat(push.get().branch()).isEqualTo(BRANCH);
        assertThat(push.get().revision()).isEqualTo(REVISION);

        if (reportsChangedPaths()) {
            assertThat(push.get().hasPathDetail()).isTrue();
            assertThat(push.get().changedPaths()).contains("docs/onboarding.md");
        } else {
            // Not a defect: Azure DevOps genuinely does not report paths, and the distinction has to
            // survive normalization so ingestion falls back to a full walk rather than concluding
            // that nothing changed.
            assertThat(push.get().hasPathDetail()).isFalse();
        }
    }

    @Test
    @DisplayName("an event we do not act on is empty, not an error -- providers send plenty of them")
    void ignoresEventsThatAreNotPushes() {
        assertThat(adapter(properties()).parsePush(nonPushWebhook())).isEmpty();
    }

    @Test
    @DisplayName("a provider 500 becomes a retryable SourceAdapterException, not a leaked HTTP error")
    void translatesServerErrorsAsRetryable() {
        routes.clear();
        registerAllRoutesAs(exchange -> respond(exchange, 503, "{\"message\":\"unavailable\"}"));

        assertThatThrownBy(() -> adapter(properties()).listTree(descriptor(), REVISION, ""))
                .isInstanceOfSatisfying(SourceAdapterException.class, e -> {
                    assertThat(e.getSourceType()).isEqualTo(expectedType());
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("a 401 is not retryable: a wrong token stays wrong, and retrying burns rate limit")
    void translatesAuthorizationErrorsAsPermanent() {
        routes.clear();
        registerAllRoutesAs(exchange -> respond(exchange, 401, "{\"message\":\"bad credentials\"}"));

        assertThatThrownBy(() -> adapter(properties()).listTree(descriptor(), REVISION, ""))
                .isInstanceOfSatisfying(SourceAdapterException.class,
                        e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    @DisplayName("a rate limit is retryable, because waiting genuinely helps")
    void translatesRateLimitingAsRetryable() {
        routes.clear();
        registerAllRoutesAs(exchange -> respond(exchange, 429, "{\"message\":\"rate limited\"}"));

        assertThatThrownBy(() -> adapter(properties()).listTree(descriptor(), REVISION, ""))
                .isInstanceOfSatisfying(SourceAdapterException.class,
                        e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    @DisplayName("the descriptor never prints its token, so it is safe in logs and stack traces")
    void keepsTheTokenOutOfToString() {
        assertThat(descriptor().toString()).doesNotContain("secret-token");
    }

    protected SourceProperties properties() {
        return new SourceProperties(null, Duration.ofSeconds(5), 1_000_000, 1000);
    }

    protected RepositoryDescriptor descriptor() {
        return new RepositoryDescriptor(expectedType(), OWNER, REPOSITORY, PROJECT, BRANCH, "",
                baseUrl, "secret-token");
    }

    protected void route(String prefix, Handler handler) {
        routes.put(prefix, handler);
    }

    private void registerAllRoutesAs(Handler handler) {
        route("/", handler);
    }

    protected static void respond(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name)) {
                return parts.length > 1
                        ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    protected static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A stub provider endpoint. */
    @FunctionalInterface
    protected interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}

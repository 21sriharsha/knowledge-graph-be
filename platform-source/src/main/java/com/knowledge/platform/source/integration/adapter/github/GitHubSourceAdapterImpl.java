package com.knowledge.platform.source.integration.adapter.github;

import com.knowledge.platform.source.integration.adapter.AbstractRestSourceAdapter;
import com.knowledge.platform.source.integration.adapter.SourceAdapterException;
import com.knowledge.platform.source.model.dto.PushNotification;
import com.knowledge.platform.source.model.dto.RemoteEntry;
import com.knowledge.platform.source.model.dto.RepositoryDescriptor;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * GitHub provider adapter.
 *
 * <p>Provider quirks handled here, and nowhere else in the application:
 *
 * <ul>
 *   <li>The Git tree API returns the whole repository in one recursive call, and sets
 *       {@code truncated} rather than paginating when the tree is too large.
 *   <li>File content arrives base64-encoded with embedded newlines that must be stripped before
 *       decoding.
 *   <li>Webhook authenticity is an HMAC-SHA256 over the raw body, sent as
 *       {@code X-Hub-Signature-256: sha256=<hex>}.
 *   <li>A push payload lists added, modified and removed paths per commit, which has to be flattened
 *       across commits.
 * </ul>
 */
@Slf4j
@Component
public class GitHubSourceAdapterImpl extends AbstractRestSourceAdapter {

    private static final String DEFAULT_API_BASE_URL = "https://api.github.com";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String EVENT_HEADER = "X-GitHub-Event";
    private static final String DELIVERY_HEADER = "X-GitHub-Delivery";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PUSH_EVENT = "push";
    private static final String BRANCH_REF_PREFIX = "refs/heads/";

    private final ObjectMapper objectMapper;

    public GitHubSourceAdapterImpl(SourceProperties properties, ObjectMapper objectMapper) {
        super(properties);
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType providerType() {
        return SourceType.GITHUB;
    }

    @Override
    public List<RemoteEntry> listTree(
            RepositoryDescriptor descriptor, String revision, String pathPrefix) {
        logProviderCall(descriptor, "listTree");
        GitHubApiModels.Tree tree = call(SourceType.GITHUB, "GitHub tree listing", () ->
                client(descriptor)
                        .get()
                        .uri("/repos/{owner}/{repo}/git/trees/{ref}?recursive=1",
                                descriptor.owner(), descriptor.repository(), revision)
                        .retrieve()
                        .body(GitHubApiModels.Tree.class));

        if (tree == null || tree.tree() == null) {
            return List.of();
        }
        if (tree.truncated()) {
            // Not silently ignored: a truncated tree means files exist that ingestion will never see,
            // and an author whose article is missing deserves an explanation that exists somewhere.
            log.warn("GitHub truncated the tree listing for {}; content beyond the limit will not be "
                    + "ingested. Narrow knowledge.source content-path to reduce the tree size.", descriptor);
        }

        List<RemoteEntry> entries = new ArrayList<>();
        for (GitHubApiModels.TreeEntry entry : tree.tree()) {
            if (!entry.isFile() || !matchesPrefix(entry.path(), pathPrefix)) {
                continue;
            }
            entries.add(new RemoteEntry(entry.path(), entry.sha(),
                    entry.size() == null ? -1 : entry.size()));
            if (entries.size() >= properties.maxTreeEntries()) {
                log.warn("Stopped listing {} at {} entries (knowledge.source.max-tree-entries)",
                        descriptor, properties.maxTreeEntries());
                break;
            }
        }
        return entries;
    }

    @Override
    public Optional<SourceFile> readFile(
            RepositoryDescriptor descriptor, String path, String revision) {
        GitHubApiModels.FileContent content;
        try {
            content = call(SourceType.GITHUB, "GitHub file read", () ->
                    client(descriptor)
                            .get()
                            .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                                    descriptor.owner(), descriptor.repository(), path, revision)
                            .retrieve()
                            .body(GitHubApiModels.FileContent.class));
        } catch (SourceAdapterException e) {
            if (isNotFound(e)) {
                // A deleted file is an expected outcome of processing a push, not a failure.
                return Optional.empty();
            }
            throw e;
        }

        if (content == null || content.content() == null) {
            return Optional.empty();
        }
        if (content.size() != null && content.size() > properties.maxFileBytes()) {
            log.warn("Skipping {} in {}: {} bytes exceeds knowledge.source.max-file-bytes",
                    path, descriptor, content.size());
            return Optional.empty();
        }
        return Optional.of(new SourceFile(path, decode(content), revision, content.sha()));
    }

    @Override
    public Optional<String> resolveHeadRevision(RepositoryDescriptor descriptor, String branch) {
        GitHubApiModels.Commit commit = call(SourceType.GITHUB, "GitHub head revision", () ->
                client(descriptor)
                        .get()
                        .uri("/repos/{owner}/{repo}/commits/{branch}",
                                descriptor.owner(), descriptor.repository(), branch)
                        .retrieve()
                        .body(GitHubApiModels.Commit.class));
        return Optional.ofNullable(commit).map(GitHubApiModels.Commit::sha);
    }

    @Override
    public boolean verifyWebhook(WebhookRequest request, String secret) {
        String provided = request.header(SIGNATURE_HEADER);
        if (provided == null || secret == null || !provided.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = SIGNATURE_PREFIX + HexFormat.of().formatHex(
                    mac.doFinal(request.rawBody().getBytes(StandardCharsets.UTF_8)));
            return secretsMatch(expected, provided);
        } catch (java.security.GeneralSecurityException e) {
            log.error("Could not compute the GitHub webhook signature", e);
            return false;
        }
    }

    @Override
    public Optional<PushNotification> parsePush(WebhookRequest request) {
        if (!PUSH_EVENT.equalsIgnoreCase(request.header(EVENT_HEADER))) {
            // Ping, issues, stars: everything else is a legitimate event we simply do not act on.
            return Optional.empty();
        }
        try {
            GitHubApiModels.PushPayload payload =
                    objectMapper.readValue(request.rawBody(), GitHubApiModels.PushPayload.class);
            if (payload.ref() == null || !payload.ref().startsWith(BRANCH_REF_PREFIX)) {
                // A tag push carries no branch content to ingest.
                return Optional.empty();
            }

            List<String> changed = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            if (payload.commits() != null) {
                for (GitHubApiModels.PushCommit commit : payload.commits()) {
                    addAll(changed, commit.added());
                    addAll(changed, commit.modified());
                    addAll(removed, commit.removed());
                }
            }
            // A path modified in one commit and deleted in a later one within the same push is a
            // deletion; taking the union in the other order would resurrect it.
            changed.removeAll(removed);

            String revision = payload.after() != null ? payload.after()
                    : payload.headCommit() == null ? null : payload.headCommit().id();

            return Optional.of(new PushNotification(
                    request.header(DELIVERY_HEADER),
                    payload.ref().substring(BRANCH_REF_PREFIX.length()),
                    revision,
                    changed,
                    removed));
        } catch (JacksonException e) {
            throw new SourceAdapterException(
                    SourceType.GITHUB, "Unreadable GitHub push payload", false, e);
        }
    }

    private RestClient client(RepositoryDescriptor descriptor) {
        String baseUrl = descriptor.apiBaseUrl() == null || descriptor.apiBaseUrl().isBlank()
                ? DEFAULT_API_BASE_URL
                : descriptor.apiBaseUrl();
        return clientFor(baseUrl, headers -> {
            headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");
            if (descriptor.accessToken() != null && !descriptor.accessToken().isBlank()) {
                headers.setBearerAuth(descriptor.accessToken());
            }
        });
    }

    /** GitHub wraps base64 content at 60 characters; the decoder rejects the embedded newlines. */
    private String decode(GitHubApiModels.FileContent content) {
        if (!"base64".equalsIgnoreCase(content.encoding())) {
            return content.content();
        }
        byte[] decoded = Base64.getMimeDecoder().decode(content.content());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private boolean matchesPrefix(String path, String prefix) {
        return prefix == null || prefix.isBlank() || path.startsWith(prefix + "/") || path.equals(prefix);
    }

    private void addAll(List<String> target, List<String> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    private boolean isNotFound(SourceAdapterException e) {
        return e.getCause() instanceof HttpClientErrorException clientError
                && clientError.getStatusCode() == HttpStatus.NOT_FOUND;
    }
}

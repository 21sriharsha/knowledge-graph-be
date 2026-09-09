package com.knowledge.platform.source.integration.adapter.gitlab;

import com.knowledge.platform.source.integration.adapter.AbstractRestSourceAdapter;
import com.knowledge.platform.source.integration.adapter.SourceAdapterException;
import com.knowledge.platform.source.model.dto.PushNotification;
import com.knowledge.platform.source.model.dto.RemoteEntry;
import com.knowledge.platform.source.model.dto.RepositoryDescriptor;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * GitLab provider adapter.
 *
 * <p>Provider quirks handled here, and nowhere else:
 *
 * <ul>
 *   <li>Projects are addressed by a URL-encoded {@code owner/repo} path, so the slash must be encoded
 *       as {@code %2F} -- and encoded again where the value is itself a path segment.
 *   <li>The tree API paginates; a repository is only fully listed by following pages.
 *   <li>Webhook authenticity is a plain shared token in {@code X-Gitlab-Token}, not a MAC over the
 *       body. That is weaker than GitHub's scheme and is GitLab's design, not a shortcut taken here;
 *       the comparison is still constant-time.
 * </ul>
 */
@Slf4j
@Component
public class GitLabSourceAdapterImpl extends AbstractRestSourceAdapter {

    private static final String DEFAULT_API_BASE_URL = "https://gitlab.com";
    private static final String TOKEN_HEADER = "X-Gitlab-Token";
    private static final String EVENT_HEADER = "X-Gitlab-Event";
    private static final String DELIVERY_HEADER = "X-Gitlab-Event-UUID";
    private static final String PUSH_EVENT = "Push Hook";
    private static final String BRANCH_REF_PREFIX = "refs/heads/";
    private static final int PAGE_SIZE = 100;

    private final ObjectMapper objectMapper;

    public GitLabSourceAdapterImpl(SourceProperties properties, ObjectMapper objectMapper) {
        super(properties);
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType providerType() {
        return SourceType.GITLAB;
    }

    @Override
    public List<RemoteEntry> listTree(
            RepositoryDescriptor descriptor, String revision, String pathPrefix) {
        logProviderCall(descriptor, "listTree");
        List<RemoteEntry> entries = new ArrayList<>();
        String projectId = projectId(descriptor);

        for (int page = 1; entries.size() < properties.maxTreeEntries(); page++) {
            final int currentPage = page;
            List<GitLabApiModels.TreeEntry> batch = call(SourceType.GITLAB, "GitLab tree listing", () ->
                    client(descriptor)
                            .get()
                            .uri(builder -> builder
                                    .path("/api/v4/projects/{id}/repository/tree")
                                    .queryParam("ref", revision)
                                    .queryParam("recursive", true)
                                    .queryParam("per_page", PAGE_SIZE)
                                    .queryParam("page", currentPage)
                                    .queryParam("path", pathPrefix == null ? "" : pathPrefix)
                                    .build(projectId))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<GitLabApiModels.TreeEntry>>() {
                            }));

            if (batch == null || batch.isEmpty()) {
                break;
            }
            batch.stream()
                    .filter(GitLabApiModels.TreeEntry::isFile)
                    .forEach(entry -> entries.add(new RemoteEntry(entry.path(), entry.id(), -1)));

            // A short page is the last page. GitLab also returns pagination headers, but a size check
            // needs no header parsing and behaves correctly when they are absent behind a proxy.
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }

        if (entries.size() >= properties.maxTreeEntries()) {
            log.warn("Stopped listing {} at {} entries (knowledge.source.max-tree-entries)",
                    descriptor, properties.maxTreeEntries());
        }
        return entries;
    }

    @Override
    public Optional<SourceFile> readFile(
            RepositoryDescriptor descriptor, String path, String revision) {
        GitLabApiModels.FileContent content;
        try {
            content = call(SourceType.GITLAB, "GitLab file read", () ->
                    client(descriptor)
                            .get()
                            .uri("/api/v4/projects/{id}/repository/files/{path}?ref={ref}",
                                    projectId(descriptor), encode(path), revision)
                            .retrieve()
                            .body(GitLabApiModels.FileContent.class));
        } catch (SourceAdapterException e) {
            if (isNotFound(e)) {
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

        String decoded = "base64".equalsIgnoreCase(content.encoding())
                ? new String(Base64.getMimeDecoder().decode(content.content()), StandardCharsets.UTF_8)
                : content.content();
        return Optional.of(new SourceFile(path, decoded, revision, content.blobId()));
    }

    @Override
    public Optional<String> resolveHeadRevision(RepositoryDescriptor descriptor, String branch) {
        GitLabApiModels.Commit commit = call(SourceType.GITLAB, "GitLab head revision", () ->
                client(descriptor)
                        .get()
                        .uri("/api/v4/projects/{id}/repository/commits/{branch}",
                                projectId(descriptor), branch)
                        .retrieve()
                        .body(GitLabApiModels.Commit.class));
        return Optional.ofNullable(commit).map(GitLabApiModels.Commit::id);
    }

    @Override
    public boolean verifyWebhook(WebhookRequest request, String secret) {
        return secretsMatch(secret, request.header(TOKEN_HEADER));
    }

    @Override
    public Optional<PushNotification> parsePush(WebhookRequest request) {
        if (!PUSH_EVENT.equalsIgnoreCase(request.header(EVENT_HEADER))) {
            return Optional.empty();
        }
        try {
            GitLabApiModels.PushPayload payload =
                    objectMapper.readValue(request.rawBody(), GitLabApiModels.PushPayload.class);
            if (payload.ref() == null || !payload.ref().startsWith(BRANCH_REF_PREFIX)) {
                return Optional.empty();
            }

            List<String> changed = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            if (payload.commits() != null) {
                for (GitLabApiModels.PushCommit commit : payload.commits()) {
                    addAll(changed, commit.added());
                    addAll(changed, commit.modified());
                    addAll(removed, commit.removed());
                }
            }
            changed.removeAll(removed);

            return Optional.of(new PushNotification(
                    request.header(DELIVERY_HEADER),
                    payload.ref().substring(BRANCH_REF_PREFIX.length()),
                    payload.checkoutSha(),
                    changed,
                    removed));
        } catch (JacksonException e) {
            throw new SourceAdapterException(
                    SourceType.GITLAB, "Unreadable GitLab push payload", false, e);
        }
    }

    private RestClient client(RepositoryDescriptor descriptor) {
        String baseUrl = descriptor.apiBaseUrl() == null || descriptor.apiBaseUrl().isBlank()
                ? DEFAULT_API_BASE_URL
                : descriptor.apiBaseUrl();
        return clientFor(baseUrl, headers -> {
            if (descriptor.accessToken() != null && !descriptor.accessToken().isBlank()) {
                headers.set("PRIVATE-TOKEN", descriptor.accessToken());
            }
        });
    }

    /** GitLab addresses a project by its URL-encoded {@code namespace/project} path. */
    private String projectId(RepositoryDescriptor descriptor) {
        return descriptor.owner() + "/" + descriptor.repository();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

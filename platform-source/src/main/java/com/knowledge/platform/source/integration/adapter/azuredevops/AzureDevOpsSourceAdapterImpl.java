package com.knowledge.platform.source.integration.adapter.azuredevops;

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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Azure DevOps provider adapter.
 *
 * <p>Provider quirks handled here, and nowhere else:
 *
 * <ul>
 *   <li>Repositories are addressed as organisation/project/repository, the only provider with a
 *       project level -- which is why {@code SourceType.requiresProject()} exists.
 *   <li>Every API call must carry an explicit {@code api-version}; omitting it is an error rather
 *       than a default.
 *   <li>Authentication is HTTP Basic with an empty username and the PAT as the password.
 *   <li>Collections come wrapped in {@code {count, value}} rather than as bare arrays.
 *   <li><b>Push payloads carry no file list.</b> Unlike GitHub and GitLab, the service hook reports
 *       commits but not the paths they touched, so a push here always produces a
 *       {@link PushNotification} without path detail and ingestion falls back to a full walk. This
 *       is exactly the case {@link PushNotification#hasPathDetail()} exists to represent -- flattening
 *       it to an empty change list would look like "a push that changed nothing".
 *   <li>Service hooks have no signature scheme; authenticity rests on a secret configured as the
 *       endpoint's Basic auth password.
 * </ul>
 */
@Slf4j
@Component
public class AzureDevOpsSourceAdapterImpl extends AbstractRestSourceAdapter {

    private static final String DEFAULT_API_BASE_URL = "https://dev.azure.com";
    private static final String API_VERSION = "7.1";
    private static final String PUSH_EVENT_TYPE = "git.push";
    private static final String BRANCH_REF_PREFIX = "refs/heads/";

    private final ObjectMapper objectMapper;

    public AzureDevOpsSourceAdapterImpl(SourceProperties properties, ObjectMapper objectMapper) {
        super(properties);
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType providerType() {
        return SourceType.AZURE_DEVOPS;
    }

    @Override
    public List<RemoteEntry> listTree(
            RepositoryDescriptor descriptor, String revision, String pathPrefix) {
        logProviderCall(descriptor, "listTree");
        AzureDevOpsApiModels.Envelope<AzureDevOpsApiModels.Item> envelope =
                call(SourceType.AZURE_DEVOPS, "Azure DevOps item listing", () ->
                        client(descriptor)
                                .get()
                                .uri(builder -> builder
                                        .path("/{owner}/{project}/_apis/git/repositories/{repo}/items")
                                        .queryParam("recursionLevel", "Full")
                                        .queryParam("scopePath",
                                                pathPrefix == null || pathPrefix.isBlank() ? "/" : "/" + pathPrefix)
                                        .queryParam("versionDescriptor.version", revision)
                                        .queryParam("versionDescriptor.versionType", "commit")
                                        .queryParam("api-version", API_VERSION)
                                        .build(descriptor.owner(), descriptor.project(),
                                                descriptor.repository()))
                                .retrieve()
                                .body(new ParameterizedTypeReference<
                                        AzureDevOpsApiModels.Envelope<AzureDevOpsApiModels.Item>>() {
                                }));

        if (envelope == null || envelope.value() == null) {
            return List.of();
        }
        return envelope.value().stream()
                .filter(AzureDevOpsApiModels.Item::isFile)
                .limit(properties.maxTreeEntries())
                // Azure DevOps returns absolute repository paths ("/docs/a.md"); every other provider
                // and the rest of this platform use repository-relative paths.
                .map(item -> new RemoteEntry(stripLeadingSlash(item.path()), item.objectId(), -1))
                .toList();
    }

    @Override
    public Optional<SourceFile> readFile(
            RepositoryDescriptor descriptor, String path, String revision) {
        AzureDevOpsApiModels.Item item;
        try {
            item = call(SourceType.AZURE_DEVOPS, "Azure DevOps file read", () ->
                    client(descriptor)
                            .get()
                            .uri(builder -> builder
                                    .path("/{owner}/{project}/_apis/git/repositories/{repo}/items")
                                    .queryParam("path", "/" + stripLeadingSlash(path))
                                    .queryParam("includeContent", true)
                                    .queryParam("$format", "json")
                                    .queryParam("versionDescriptor.version", revision)
                                    .queryParam("versionDescriptor.versionType", "commit")
                                    .queryParam("api-version", API_VERSION)
                                    .build(descriptor.owner(), descriptor.project(),
                                            descriptor.repository()))
                            .retrieve()
                            .body(AzureDevOpsApiModels.Item.class));
        } catch (SourceAdapterException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw e;
        }

        if (item == null || item.content() == null) {
            return Optional.empty();
        }
        if (item.content().length() > properties.maxFileBytes()) {
            log.warn("Skipping {} in {}: content exceeds knowledge.source.max-file-bytes",
                    path, descriptor);
            return Optional.empty();
        }
        // With $format=json Azure DevOps returns the content as plain text, not base64.
        return Optional.of(new SourceFile(
                stripLeadingSlash(path), item.content(), revision, item.objectId()));
    }

    @Override
    public Optional<String> resolveHeadRevision(RepositoryDescriptor descriptor, String branch) {
        AzureDevOpsApiModels.Envelope<AzureDevOpsApiModels.Commit> envelope =
                call(SourceType.AZURE_DEVOPS, "Azure DevOps head revision", () ->
                        client(descriptor)
                                .get()
                                .uri(builder -> builder
                                        .path("/{owner}/{project}/_apis/git/repositories/{repo}/commits")
                                        .queryParam("searchCriteria.itemVersion.version", branch)
                                        .queryParam("searchCriteria.$top", 1)
                                        .queryParam("api-version", API_VERSION)
                                        .build(descriptor.owner(), descriptor.project(),
                                                descriptor.repository()))
                                .retrieve()
                                .body(new ParameterizedTypeReference<
                                        AzureDevOpsApiModels.Envelope<AzureDevOpsApiModels.Commit>>() {
                                }));

        if (envelope == null || envelope.value() == null || envelope.value().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(envelope.value().getFirst().commitId());
    }

    @Override
    public boolean verifyWebhook(WebhookRequest request, String secret) {
        String authorization = request.header(HttpHeaders.AUTHORIZATION);
        if (authorization == null || secret == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(authorization.substring("Basic ".length()).trim()),
                    StandardCharsets.UTF_8);
            // Azure DevOps service hooks send "username:password"; the secret is the password half.
            int separator = decoded.indexOf(':');
            String provided = separator < 0 ? decoded : decoded.substring(separator + 1);
            return secretsMatch(secret, provided);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Optional<PushNotification> parsePush(WebhookRequest request) {
        try {
            AzureDevOpsApiModels.PushPayload payload =
                    objectMapper.readValue(request.rawBody(), AzureDevOpsApiModels.PushPayload.class);
            if (!PUSH_EVENT_TYPE.equalsIgnoreCase(payload.eventType())
                    || payload.resource() == null
                    || payload.resource().refUpdates() == null
                    || payload.resource().refUpdates().isEmpty()) {
                return Optional.empty();
            }

            AzureDevOpsApiModels.RefUpdate refUpdate = payload.resource().refUpdates().getFirst();
            if (refUpdate.name() == null || !refUpdate.name().startsWith(BRANCH_REF_PREFIX)) {
                return Optional.empty();
            }

            // Empty change lists, deliberately: the payload carries no paths, so hasPathDetail() is
            // false and ingestion performs a full walk rather than concluding nothing changed.
            return Optional.of(new PushNotification(
                    payload.id(),
                    refUpdate.name().substring(BRANCH_REF_PREFIX.length()),
                    refUpdate.newObjectId(),
                    List.of(),
                    List.of()));
        } catch (JacksonException e) {
            throw new SourceAdapterException(
                    SourceType.AZURE_DEVOPS, "Unreadable Azure DevOps push payload", false, e);
        }
    }

    private RestClient client(RepositoryDescriptor descriptor) {
        String baseUrl = descriptor.apiBaseUrl() == null || descriptor.apiBaseUrl().isBlank()
                ? DEFAULT_API_BASE_URL
                : descriptor.apiBaseUrl();
        return clientFor(baseUrl, headers -> {
            headers.set(HttpHeaders.ACCEPT, "application/json");
            if (descriptor.accessToken() != null && !descriptor.accessToken().isBlank()) {
                // Personal access tokens authenticate as Basic with an empty username.
                headers.setBasicAuth("", descriptor.accessToken(), StandardCharsets.UTF_8);
            }
        });
    }

    private String stripLeadingSlash(String path) {
        return path != null && path.startsWith("/") ? path.substring(1) : path;
    }

    private boolean isNotFound(SourceAdapterException e) {
        return e.getCause() instanceof HttpClientErrorException clientError
                && clientError.getStatusCode() == HttpStatus.NOT_FOUND;
    }
}

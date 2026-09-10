package com.knowledge.platform.source.service;

import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.source.integration.SourceIntegrationFactory;
import com.knowledge.platform.source.integration.strategy.SourceIntegrationStrategy;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.source.repository.SourceRepositoryRepository;
import com.knowledge.platform.author.model.dto.StudioPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default SourceService.
 *
 * <p>See {@link SourceService} for what this provides and why it exists.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class DefaultSourceServiceImpl implements SourceService {

    private final SourceRepositoryRepository repositories;
    private final SourceIntegrationFactory integrationFactory;
    private final SourceCredentialCipher credentialCipher;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthorService authorService;

    public DefaultSourceServiceImpl(
            SourceRepositoryRepository repositories,
            SourceIntegrationFactory integrationFactory,
            SourceCredentialCipher credentialCipher,
            ApplicationEventPublisher eventPublisher,
            AuthorService authorService) {
        this.repositories = repositories;
        this.integrationFactory = integrationFactory;
        this.credentialCipher = credentialCipher;
        this.eventPublisher = eventPublisher;
        this.authorService = authorService;
    }

    @Override
    public SourceRepository requireById(UUID id) {
        return repositories.findById(id)
                .orElseThrow(() -> NotFoundException.of("Source repository", id));
    }

    @Override
    public Optional<SourceRepository> findById(UUID id) {
        return repositories.findById(id);
    }

    @Override
    public SourceRepository requireOwned(UUID id, StudioPrincipal principal) {
        SourceRepository repository = requireById(id);
        if (!principal.canActOnBehalfOf(repository.getOwnerAuthorId())) {
            // Deliberately the same exception as "no such repository": a distinguishable refusal
            // would confirm the id belongs to someone, which is the thing worth not confirming.
            throw NotFoundException.of("Source repository", id);
        }
        return repository;
    }

    @Override
    public List<SourceRepository> findAll() {
        return repositories.findAll();
    }

    @Override
    public List<SourceRepository> findVisible(StudioPrincipal principal) {
        if (principal.isAdmin()) {
            return repositories.findAll();
        }
        if (principal.authorId() == null) {
            // Signed in, no byline: owns nothing, so sees nothing. An empty studio is the honest
            // view for someone an administrator has not yet linked to an author.
            return List.of();
        }
        return repositories.findByOwnerAuthorId(principal.authorId());
    }

    @Override
    public List<SourceRepository> findActive() {
        return repositories.findByActiveTrue();
    }

    @Override
    @Transactional
    public SourceRepository connect(
            SourceType sourceType, String displayName, String owner, String repository,
            String project, String defaultBranch, String contentPath, String apiBaseUrl,
            String accessToken, String webhookSecret,
            String ownerAuthorName, String ownerAuthorEmail, UUID explicitOwnerAuthorId) {

        SourceRepository connected = SourceRepository.create(
                sourceType, displayName, owner, repository, project, defaultBranch, contentPath);

        // Resolve the owning author now rather than waiting for ingestion to infer one. Creating the
        // identity here is what lets a repository's articles omit author frontmatter entirely.
        //
        // An explicit id wins over a name: it is what the studio passes for the signed-in author, and
        // resolving their byline by name instead could match a different author, or mint a second.
        UUID ownerAuthorId = explicitOwnerAuthorId != null
                ? explicitOwnerAuthorId
                : ownerAuthorName == null || ownerAuthorName.isBlank()
                        ? null
                        : authorService.findOrCreateByName(ownerAuthorName, ownerAuthorEmail).getId();

        connected.updateSettings(displayName, defaultBranch, contentPath, apiBaseUrl, ownerAuthorId);

        // Provider-specific validation before anything is stored, so a misconfigured connection is
        // rejected at the API rather than discovered by a background job hours later.
        integrationFactory.strategyFor(sourceType).validateConfiguration(connected);

        connected.storeCredentials(
                credentialCipher.encrypt(accessToken), credentialCipher.encrypt(webhookSecret));
        return repositories.save(connected);
    }

    @Override
    @Transactional
    public SourceRepository update(
            UUID id, String displayName, String defaultBranch, String contentPath,
            String apiBaseUrl, UUID ownerAuthorId, String accessToken, String webhookSecret) {
        SourceRepository repository = requireById(id);
        repository.updateSettings(displayName, defaultBranch, contentPath, apiBaseUrl, ownerAuthorId);
        // Null means "leave the stored credential alone"; the cipher returns null for blank input,
        // and storeCredentials ignores nulls. An operator changing a content path need not re-enter
        // a token they no longer have.
        repository.storeCredentials(
                credentialCipher.encrypt(accessToken), credentialCipher.encrypt(webhookSecret));
        return repositories.save(repository);
    }

    @Override
    @Transactional
    public SourceRepository setActive(UUID id, boolean active) {
        SourceRepository repository = requireById(id);
        if (active) {
            repository.activate();
        } else {
            repository.deactivate();
        }
        return repositories.save(repository);
    }

    @Override
    @Transactional
    public void requestSync(UUID id, SourceSyncRequestedEvent.Trigger trigger) {
        SourceRepository repository = requireById(id);
        if (!repository.isActive()) {
            log.info("Ignoring sync request for inactive {}", repository);
            return;
        }
        String revision = integrationFactory.strategyFor(repository)
                .resolveCurrentRevision(repository)
                .orElse(repository.getDefaultBranch());
        eventPublisher.publishEvent(
                SourceSyncRequestedEvent.fullResync(repository.getId(), revision, trigger));
    }

    @Override
    public void publishSync(SourceSyncRequestedEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public List<SourceFile> fetchContentSnapshot(SourceRepository repository, String revision) {
        return strategyFor(repository).fetchContentSnapshot(repository, revision);
    }

    @Override
    public Optional<SourceFile> fetchFile(
            SourceRepository repository, String path, String revision) {
        return strategyFor(repository).fetchFile(repository, path, revision);
    }

    @Override
    public Optional<String> resolveCurrentRevision(SourceRepository repository) {
        return strategyFor(repository).resolveCurrentRevision(repository);
    }

    @Override
    public SourceIntegrationStrategy strategyFor(SourceRepository repository) {
        return integrationFactory.strategyFor(repository);
    }

    @Override
    @Transactional
    public void recordSync(UUID repositoryId, String revision) {
        repositories.findById(repositoryId).ifPresent(repository -> {
            repository.recordSync(revision);
            repositories.save(repository);
        });
    }
}

package com.knowledge.platform.source.service;

import com.knowledge.platform.source.integration.strategy.SourceIntegrationStrategy;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Optional;
import com.knowledge.platform.author.model.dto.StudioPrincipal;
import java.util.UUID;

/**
 * The source module's application boundary.
 *
 * <p>Owns connected repositories and the credentials for them, and is the only thing that turns a
 * repository into a strategy. Ingestion asks this service for content; it never touches an adapter.
 */
public interface SourceService {

    /**
     * Unrestricted lookup, for callers acting on the platform's behalf rather than a person's --
     * ingestion, webhook dispatch, the reconciliation scheduler.
     *
     * <p>Never call this from a studio path. {@link #requireOwned} is the one that asks whether the
     * caller is allowed to see it.
     */
    SourceRepository requireById(UUID id);

    /**
     * The repository, if this caller is entitled to it.
     *
     * <p>The ownership check lives inside the lookup rather than beside it, so that forgetting it
     * leaves you with no repository to act on. A separate {@code assertOwns} call would be a step
     * a future endpoint could omit and still compile.
     *
     * <p>Signals a repository owned by somebody else exactly as it signals one that does not exist.
     * A distinguishable "forbidden" would confirm the id is real to anyone enumerating.
     */
    SourceRepository requireOwned(UUID id, StudioPrincipal principal);

    /** Unrestricted, and absent rather than throwing when there is no such repository. */
    Optional<SourceRepository> findById(UUID id);

    /** Every repository, for platform-level callers. */
    List<SourceRepository> findAll();

    /**
     * The repositories this caller may see.
     *
     * <p>Filtering matters as much as refusing to mutate: a list that returns everything leaks
     * repository names, owners and content paths to anyone with a studio login, which is most of
     * what there is to know about someone else's setup.
     */
    List<SourceRepository> findVisible(StudioPrincipal principal);

    List<SourceRepository> findActive();

    /**
     * Connects a repository, optionally binding it to an owning author.
     *
     * <p>{@code ownerAuthorName} exists for the one-repository-per-author topology: the repository
     * states who it belongs to, and its articles inherit that without every file repeating an
     * {@code author:} line. The author identity is created if it is not already known.
     */
    SourceRepository connect(
            SourceType sourceType, String displayName, String owner, String repository,
            String project, String defaultBranch, String contentPath, String apiBaseUrl,
            String accessToken, String webhookSecret,
            String ownerAuthorName, String ownerAuthorEmail, UUID explicitOwnerAuthorId);

    SourceRepository update(
            UUID id, String displayName, String defaultBranch, String contentPath,
            String apiBaseUrl, UUID ownerAuthorId, String accessToken, String webhookSecret);

    SourceRepository setActive(UUID id, boolean active);

    /**
     * Asks for a repository to be fully re-materialized.
     *
     * <p>Publishes rather than calls: ingestion depends on source, so the reverse call would be a
     * dependency cycle the Maven reactor rejects. The event is published inside the transaction and
     * ingestion listens after commit, so a listener can never see a repository that was rolled back.
     */
    void requestSync(UUID id, SourceSyncRequestedEvent.Trigger trigger);

    /** Publishes a sync for specific paths, used by the webhook path. */
    void publishSync(SourceSyncRequestedEvent event);

    List<SourceFile> fetchContentSnapshot(SourceRepository repository, String revision);

    Optional<SourceFile> fetchFile(
            SourceRepository repository, String path, String revision);

    Optional<String> resolveCurrentRevision(SourceRepository repository);

    SourceIntegrationStrategy strategyFor(SourceRepository repository);

    void recordSync(UUID repositoryId, String revision);
}

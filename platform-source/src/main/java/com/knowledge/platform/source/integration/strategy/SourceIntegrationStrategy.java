package com.knowledge.platform.source.integration.strategy;

import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookOutcome;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Optional;

/**
 * Provider-neutral source operations, as the application wants to express them.
 *
 * <p>The distinction from {@link com.knowledge.platform.source.integration.adapter.SourceAdapter} is
 * the point of the pattern. An <em>adapter</em> answers "how does this provider's API work". A
 * <em>strategy</em> answers "what does this platform want done", in terms the ingestion module can
 * use: give me the Markdown under this repository's content path, tell me whether this webhook is
 * real and what changed.
 *
 * <p>Callers never construct one. {@link SourceIntegrationFactory} selects it from the repository's
 * {@link SourceType}, so adding a provider means adding a strategy and an adapter and changing
 * nothing else.
 */
public interface SourceIntegrationStrategy {

    /** The provider this strategy serves. The factory keys on this. */
    SourceType supportedType();

    /**
     * Whether a push from this provider says which files it touched.
     *
     * <p>GitHub and GitLab do; Azure DevOps does not. Ingestion uses this to choose between
     * processing named files and walking the repository, so the difference is answered once here
     * rather than inferred from an empty list at every call site.
     */
    boolean supportsIncrementalSync();

    /** Rejects a repository this provider cannot address, before any credential is stored. */
    void validateConfiguration(SourceRepository repository);

    /** The head revision of the repository's configured branch. */
    Optional<String> resolveCurrentRevision(SourceRepository repository);

    /**
     * Every Markdown file under the repository's content path at a revision.
     *
     * <p>Filtering to Markdown and to the content path happens here, not in the adapter: what counts
     * as content is a platform decision, and duplicating it per provider is how the three drift.
     */
    List<SourceFile> fetchContentSnapshot(SourceRepository repository, String revision);

    /** One file, or empty when it does not exist at that revision -- which is how a delete looks. */
    Optional<SourceFile> fetchFile(SourceRepository repository, String path, String revision);

    /** Verifies an inbound webhook and normalizes it. Never throws on an untrusted payload. */
    WebhookOutcome interpretWebhook(SourceRepository repository, WebhookRequest request);
}

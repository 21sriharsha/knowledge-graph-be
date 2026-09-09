package com.knowledge.platform.source.integration.adapter;

import com.knowledge.platform.source.model.dto.PushNotification;
import com.knowledge.platform.source.model.dto.RemoteEntry;
import com.knowledge.platform.source.model.dto.RepositoryDescriptor;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Optional;

/**
 * The provider boundary. Everything provider-specific lives behind this interface and nothing else
 * in the application knows a provider exists.
 *
 * <p>An adapter owns: authentication scheme, base URLs, pagination, request and response models,
 * content encoding, webhook signature scheme, payload shape, and the provider's error semantics. It
 * returns only the normalized types in {@code source.model.dto}, so a GitHub response object can
 * never reach ingestion or content.
 *
 * <p>Provider failures must not escape as provider-specific exceptions either. Adapters translate
 * them into {@link SourceAdapterException}, which is what allows the reliability rule -- a provider
 * being down records a sync failure and leaves canonical content untouched -- to be implemented once
 * rather than three times.
 */
public interface SourceAdapter {

    /** The provider this adapter speaks for. Used by the factory to bind adapters to strategies. */
    SourceType providerType();

    /**
     * Lists the repository tree at a revision.
     *
     * @param pathPrefix repository-relative prefix to restrict the walk to, or empty for the root
     * @throws SourceAdapterException when the provider cannot be reached or refuses the request
     */
    List<RemoteEntry> listTree(RepositoryDescriptor descriptor, String revision, String pathPrefix);

    /** Reads one file, decoded to UTF-8. Empty when the path does not exist at that revision. */
    Optional<SourceFile> readFile(RepositoryDescriptor descriptor, String path, String revision);

    /** Resolves a branch to its current head revision. */
    Optional<String> resolveHeadRevision(RepositoryDescriptor descriptor, String branch);

    /**
     * Verifies that a webhook genuinely came from the provider.
     *
     * <p>Returning false must be indistinguishable in cost from returning true where the scheme is a
     * MAC, so implementations compare in constant time. A webhook endpoint is unauthenticated at the
     * HTTP layer; this check is the only thing standing between an attacker and the ingestion
     * pipeline.
     */
    boolean verifyWebhook(WebhookRequest request, String secret);

    /**
     * Interprets a verified webhook as a push.
     *
     * @return empty when the event is one this platform does not act on, which is not an error
     */
    Optional<PushNotification> parsePush(WebhookRequest request);
}

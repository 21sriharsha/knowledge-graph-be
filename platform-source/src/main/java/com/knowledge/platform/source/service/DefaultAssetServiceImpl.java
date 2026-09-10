package com.knowledge.platform.source.service;

import com.knowledge.platform.source.integration.SourceIntegrationFactory;
import com.knowledge.platform.source.model.dto.BinaryAsset;
import com.knowledge.platform.source.model.entity.SourceRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default AssetService.
 *
 * <p>See {@link AssetService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultAssetServiceImpl implements AssetService {

    /** Only image types are served. See {@link #mayServe}. */
    private static final String IMAGE_PREFIX = "image/";

    private final SourceService sourceService;
    private final SourceIntegrationFactory integrationFactory;

    public DefaultAssetServiceImpl(
            SourceService sourceService, SourceIntegrationFactory integrationFactory) {
        this.sourceService = sourceService;
        this.integrationFactory = integrationFactory;
    }

    @Override
    // `#result == null` alone: Spring unwraps the Optional before evaluating this, so #result is
    // the asset itself and an empty Optional arrives as null. Calling isEmpty() on it threw.
    // Not caching absence is deliberate anyway -- an image added after a reader hit a 404 should
    // appear on their next visit, not twelve hours later.
    @Cacheable(cacheNames = "repositoryAssets", key = "#repositoryId + ':' + #path",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<BinaryAsset> fetch(UUID repositoryId, String path) {
        String normalised = normalise(path);
        if (normalised == null) {
            return Optional.empty();
        }

        Optional<SourceRepository> repository = sourceService.findById(repositoryId);
        if (repository.isEmpty()) {
            return Optional.empty();
        }

        SourceRepository source = repository.get();
        // The revision the repository last synced, not its branch head. An article and the images it
        // references should come from the same commit, or a picture can change under a paragraph
        // that describes it.
        String revision = source.getLastSyncedRevision() != null
                ? source.getLastSyncedRevision()
                : source.getDefaultBranch();

        try {
            return integrationFactory.strategyFor(source.getSourceType())
                    .readAsset(source, normalised, revision)
                    .filter(this::mayServe);
        } catch (RuntimeException e) {
            // A provider outage is not worth a 500 on a page that is otherwise fine; the image is
            // missing, which the browser already knows how to show.
            log.warn("Could not read asset '{}' from repository {}: {}",
                    normalised, repositoryId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Whether this is something the platform will serve.
     *
     * <p>Images only. Without this the endpoint is a general-purpose file server for anything in a
     * connected repository -- a `.env`, a private key, a configuration file -- reachable by anyone
     * who can guess a path, with the repository's own token used to fetch it.
     */
    private boolean mayServe(BinaryAsset asset) {
        String type = asset.contentType() == null
                ? ""
                : asset.contentType().toLowerCase(Locale.ROOT);
        if (!type.startsWith(IMAGE_PREFIX)) {
            log.debug("Refusing to serve '{}': {} is not an image", asset.path(), type);
            return false;
        }
        return true;
    }

    /**
     * Rejects anything that is not a plain repository-relative path.
     *
     * <p>Traversal is the obvious attack -- {@code ../../.git/config} would climb out of the content
     * directory -- and an absolute path or a scheme would try to leave the repository entirely.
     */
    private String normalise(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.strip();
        if (trimmed.startsWith("/") || trimmed.contains("..") || trimmed.contains("://")
                || trimmed.contains("\\") || trimmed.indexOf('\0') >= 0) {
            log.debug("Refusing an unsafe asset path: {}", trimmed);
            return null;
        }
        return trimmed;
    }
}

package com.knowledge.platform.source.service;

import com.knowledge.platform.source.model.dto.BinaryAsset;
import java.util.Optional;
import java.util.UUID;

/**
 * Serves files an article references, by fetching them from the repository they live in.
 *
 * <p>A proxy, not a store. The file already exists in the author's repository, which is its source
 * of truth; copying it here would mean a second copy to keep in step, pay for, and back up. What
 * this buys instead is that a <em>private</em> repository's images work at all: the reader's browser
 * has no token, so a raw provider URL would simply fail for them, whereas this call carries the
 * repository's stored credential.
 *
 * <p>Addressed by repository and path rather than by URL, deliberately. An endpoint that fetched
 * whatever URL it was handed would be a server-side request forgery hole -- point it at cloud
 * metadata or an internal service and it becomes a window into the network from outside. There is
 * no arbitrary URL to abuse here: the repository must be one this platform is connected to, and the
 * path is resolved within it.
 */
public interface AssetService {

    /**
     * The asset at {@code path} in the given repository, if it exists and may be served.
     *
     * <p>Empty covers every "no" -- unknown repository, missing file, a type this platform will not
     * serve. The caller answers all of them as 404, because distinguishing them tells an outsider
     * which repositories exist and what is in them.
     */
    Optional<BinaryAsset> fetch(UUID repositoryId, String path);
}

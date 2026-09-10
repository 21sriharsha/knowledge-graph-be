package com.knowledge.platform.source.controller;

import com.knowledge.platform.source.model.dto.BinaryAsset;
import com.knowledge.platform.source.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves images an article references, fetched from the repository they live in.
 *
 * <p>Public, because the articles are. It exposes only files in repositories the platform is
 * connected to, and only images -- see {@link AssetService} for why both restrictions exist and what
 * they prevent.
 *
 * <p>Addressed by repository and path rather than by URL. There is no {@code ?src=} to point
 * somewhere else, which is what keeps this from being a server-side request forgery hole.
 */
@RestController
@Tag(name = "Assets", description = "Images referenced by articles, proxied from their repository")
public class AssetController {

    /**
     * Long, because the URL is revision-scoped in practice: a repository serves assets at its last
     * synced commit, so the bytes behind one of these URLs do not change until a sync does. Without
     * this every article view would refetch every image from the provider, whose rate limits are
     * per token and shared across everything else the platform does.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/api/assets/{repositoryId}/**")
    @Operation(summary = "An image referenced by an article",
            description = """
                    Fetched live from the repository, never stored. This is what makes images in a
                    private repository work: the reader's browser has no credential, so a provider
                    URL would fail for them, and this call carries the repository's own.

                    Anything that is not an image is refused, and refusal is indistinguishable from
                    absence -- both answer 404, so the response cannot be used to discover which
                    repositories exist or what is in them.
                    """)
    public ResponseEntity<byte[]> asset(
            @PathVariable String repositoryId, jakarta.servlet.http.HttpServletRequest request) {
        // Parsed here rather than bound as a UUID: a malformed id is a request for something that
        // does not exist, and letting the framework reject it produced a 500 for what is plainly a
        // 404 -- an error page for a broken image, and an alarming one in the logs.
        UUID repository;
        try {
            repository = UUID.fromString(repositoryId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        // The path is the remainder after the repository id, and it legitimately contains slashes,
        // which a @PathVariable cannot express.
        String path = pathWithin(request, repository);

        return assetService.fetch(repository, path)
                .map(this::respond)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> respond(BinaryAsset asset) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(asset.contentType()));
        headers.setCacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic());
        headers.setContentLength(asset.sizeBytes());

        // Nothing here is markup, and saying so stops a browser from second-guessing the type and
        // rendering, say, a mislabelled HTML file as a page on this origin.
        headers.set("X-Content-Type-Options", "nosniff");

        if (!asset.rendersInline()) {
            // SVG can carry script, and script in a file served from this origin runs against this
            // origin. Forcing a download keeps an untrusted drawing from becoming an untrusted page.
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(fileNameOf(asset.path())).build());
        }
        return new ResponseEntity<>(asset.content(), headers, org.springframework.http.HttpStatus.OK);
    }

    private String pathWithin(jakarta.servlet.http.HttpServletRequest request, UUID repositoryId) {
        String uri = request.getRequestURI();
        String prefix = "/api/assets/" + repositoryId + "/";
        int start = uri.indexOf(prefix);
        return start < 0 ? "" : uri.substring(start + prefix.length());
    }

    private String fileNameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}

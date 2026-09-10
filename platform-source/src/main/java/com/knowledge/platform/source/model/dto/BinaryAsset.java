package com.knowledge.platform.source.model.dto;

/**
 * A non-text file read from a repository, held in memory only long enough to be served.
 *
 * <p>Deliberately not persisted anywhere. The platform proxies assets rather than storing them:
 * the file already exists in the author's repository, which is its source of truth, and copying it
 * would mean a second copy to keep in step and pay for. The cost of that choice is a fetch per
 * cache miss, which is why the response carries cache headers and why {@code contentType} is
 * carried from the provider rather than guessed at.
 *
 * @param contentType as reported by the provider, or inferred from the extension when it says
 *     nothing useful
 */
public record BinaryAsset(byte[] content, String contentType, String path) {

    public int sizeBytes() {
        return content == null ? 0 : content.length;
    }

    /**
     * Whether a browser may render this on the platform's own origin.
     *
     * <p>False for SVG, which is an image that can carry script -- and script in a file served from
     * this origin runs against this origin, with access to whatever a page here could reach. The
     * platform already strips raw HTML out of Markdown for exactly this reason; letting it back in
     * through an image tag would undo that. An SVG is still served, as a download.
     *
     * <p>On the asset rather than in the controller because it is a property of the file, and the
     * service that decides whether to serve it at all needs the same answer.
     */
    public boolean rendersInline() {
        return contentType != null
                && !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/svg");
    }
}

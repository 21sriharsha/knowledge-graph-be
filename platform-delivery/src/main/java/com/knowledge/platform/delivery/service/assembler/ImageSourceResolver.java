package com.knowledge.platform.delivery.service.assembler;

import com.knowledge.platform.content.model.entity.Article;
import java.util.Locale;
import java.util.UUID;

/**
 * Works out where an image an author referenced should actually be fetched from.
 *
 * <p>Authors write image paths the way they work: relative to the Markdown file, because that is
 * what renders in their editor and on the provider's own file view. Left alone, such a path resolves
 * against the article's public URL and 404s -- the image looks broken on the site while looking
 * perfect in the repository, which is the worst kind of failure to diagnose.
 *
 * <p>So a repository-relative reference becomes a link to this platform's asset endpoint, which
 * fetches it from the repository with the repository's own credential. Nothing is copied or stored,
 * and it keeps working when the repository is private -- a reader's browser has no token, so a
 * provider URL would fail for them no matter how it were written.
 *
 * <p>An absolute URL to somewhere else is left exactly as written. The platform is not a general web
 * proxy, and an author linking a diagram from elsewhere has said what they meant.
 */
final class ImageSourceResolver {

    private ImageSourceResolver() {
        throw new AssertionError("utility");
    }

    /**
     * The URL to render, or null when the reference cannot be resolved and should be reported.
     *
     * <p>Null is a real answer, not a failure: an article ingested before repositories existed, or
     * one whose repository has since been disconnected, has a relative path pointing at nothing this
     * platform can reach.
     */
    static String resolve(String reference, Article article) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String trimmed = reference.strip();

        // Already addressed absolutely, or a data: URI the author embedded. Their call, untouched.
        if (hasScheme(trimmed) || trimmed.startsWith("//")) {
            return trimmed;
        }
        // Rooted at the site, which is a deliberate reference to something this platform serves.
        if (trimmed.startsWith("/")) {
            return trimmed;
        }

        UUID repositoryId = article.getRepositoryId();
        if (repositoryId == null || article.getSourcePath() == null) {
            return null;
        }
        String withinRepository = resolveAgainst(article.getSourcePath(), trimmed);
        return withinRepository == null ? null : "/api/assets/" + repositoryId + "/" + withinRepository;
    }

    /**
     * Resolves a relative reference against the article's own location in the repository.
     *
     * <p>{@code ../} is honoured because images beside a docs directory rather than inside it is a
     * normal layout, but climbing above the repository root is not -- that is a traversal attempt,
     * and it is refused here as well as at the endpoint. Two checks for one risk, because this one
     * runs at assembly time where a mistake would be baked into a stored read model.
     */
    private static String resolveAgainst(String sourcePath, String reference) {
        String directory = sourcePath.contains("/")
                ? sourcePath.substring(0, sourcePath.lastIndexOf('/'))
                : "";

        java.util.Deque<String> segments = new java.util.ArrayDeque<>();
        if (!directory.isEmpty()) {
            for (String segment : directory.split("/")) {
                if (!segment.isEmpty()) {
                    segments.addLast(segment);
                }
            }
        }
        for (String segment : stripQuery(reference).split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    /** A trailing {@code ?v=2} or {@code #fragment} is not part of the path in the repository. */
    private static String stripQuery(String reference) {
        int cut = reference.length();
        for (char marker : new char[] {'?', '#'}) {
            int index = reference.indexOf(marker);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        return reference.substring(0, cut);
    }

    private static boolean hasScheme(String reference) {
        int colon = reference.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String candidate = reference.substring(0, colon).toLowerCase(Locale.ROOT);
        return candidate.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.');
    }
}

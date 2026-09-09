package com.knowledge.platform.content.model.dto;

/**
 * A link found in a document, before resolution.
 *
 * <p>The content module extracts links; the graph module resolves them into edges. Keeping the two
 * apart is what allows a link to be recorded as unresolved and repaired later, rather than being
 * discarded at parse time because its target did not happen to exist yet.
 *
 * @param targetReference the reference as the author wrote it
 * @param targetSlug the normalized slug the reference resolves against
 * @param displayText what to render
 * @param type how the link was written
 * @param ordinal position within the document, making the edge stable across re-ingestion
 */
public record ExtractedLink(
        String targetReference, String targetSlug, String displayText, LinkType type, int ordinal) {

    /** How an internal link was written. Both forms produce the same kind of graph edge. */
    public enum LinkType {
        /** {@code [[Target]]} or {@code [[Target|alias]]}. */
        INTERNAL_WIKI,
        /** A normal Markdown link whose href is a platform-relative path. */
        INTERNAL_SLUG
    }
}

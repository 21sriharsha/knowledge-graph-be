package com.knowledge.platform.content.model.dto;

import java.util.List;

/**
 * A Markdown document after parsing, with everything the rest of the platform needs derived from its
 * AST in a single pass.
 *
 * <p>This is the hand-off from "text an author wrote" to "content the platform can materialize".
 * Every downstream representation is built from these fields: the graph from {@link #links()},
 * the search document from {@link #plainText()}, the read model from {@link #blocks()}, the
 * embedding from {@link #plainText()}.
 *
 * <p>Canonical Markdown is carried alongside, because it -- not this record -- is the source of
 * truth. Everything here is reproducible by parsing it again.
 */
public record CanonicalDocument(
        String title,
        String summary,
        Frontmatter frontmatter,
        List<ContentBlock> blocks,
        List<DocumentHeading> outline,
        List<ExtractedLink> links,
        String plainText,
        int wordCount,
        int readingTimeMinutes,
        List<String> droppedRawHtml) {

    public CanonicalDocument {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        outline = outline == null ? List.of() : List.copyOf(outline);
        links = links == null ? List.of() : List.copyOf(links);
        droppedRawHtml = droppedRawHtml == null ? List.of() : List.copyOf(droppedRawHtml);
        frontmatter = frontmatter == null ? Frontmatter.empty() : frontmatter;
    }

    /**
     * Raw HTML the converter removed.
     *
     * <p>Reported rather than silently discarded. Dropping it is the right call -- the backend must
     * not emit HTML, and passing unsanitised markup to the frontend would make the frontend a
     * security boundary, which it is explicitly not. But an author who wrote an HTML block and sees
     * it vanish deserves an ingestion diagnostic saying so.
     */
    public boolean hasDroppedRawHtml() {
        return !droppedRawHtml.isEmpty();
    }
}

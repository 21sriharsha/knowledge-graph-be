package com.knowledge.platform.content.model.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * Inline content inside a block, as a typed tree.
 *
 * <p>The backend never generates HTML (BACKEND-SPEC section 4.8), so this is what "render
 * representation" means here: a structure the frontend maps onto its own components. That is not
 * only a layering preference -- it is the sanitisation boundary. Anything the AST does not model as
 * one of these types cannot reach the reader, so raw HTML in Markdown is dropped during conversion
 * rather than passed downstream for someone else to escape correctly.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InlineContent.TextRun.class, name = "text"),
        @JsonSubTypes.Type(value = InlineContent.StyledRun.class, name = "styled"),
        @JsonSubTypes.Type(value = InlineContent.InlineCode.class, name = "code"),
        @JsonSubTypes.Type(value = InlineContent.ExternalLink.class, name = "externalLink"),
        @JsonSubTypes.Type(value = InlineContent.InternalLink.class, name = "internalLink"),
        @JsonSubTypes.Type(value = InlineContent.ImageReference.class, name = "image"),
        @JsonSubTypes.Type(value = InlineContent.LineBreak.class, name = "break")
})
public sealed interface InlineContent {

    /** Plain text. */
    record TextRun(String text) implements InlineContent {
    }

    /** Emphasised text. Emphasis and strong share a shape and differ only by {@link Style}. */
    record StyledRun(Style style, List<InlineContent> children) implements InlineContent {
        public StyledRun {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /** A code span. Never contains nested inline content, by definition. */
    record InlineCode(String code) implements InlineContent {
    }

    /** A link out of the platform. */
    record ExternalLink(String url, String title, List<InlineContent> children)
            implements InlineContent {
        public ExternalLink {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /**
     * A link to another article.
     *
     * <p>{@code targetSlug} is the normalized reference and {@code resolved} says whether an article
     * with that slug currently exists. Unresolved links are carried through rather than degraded to
     * plain text, so the frontend can render them the way a wiki does -- visibly missing, and
     * clickable into a "not written yet" state -- instead of silently hiding the author's intent.
     */
    record InternalLink(String targetReference, String targetSlug, String displayText, boolean resolved)
            implements InlineContent {
    }

    /** An image. */
    record ImageReference(String url, String altText, String title) implements InlineContent {
    }

    /** A hard or soft line break. */
    record LineBreak(boolean hard) implements InlineContent {
    }

    /** Emphasis styles the AST distinguishes. */
    enum Style {
        EMPHASIS,
        STRONG
    }
}

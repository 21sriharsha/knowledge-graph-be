package com.knowledge.platform.content.markdown;

import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.model.dto.ContentBlock;
import com.knowledge.platform.content.model.dto.DocumentHeading;
import com.knowledge.platform.content.model.dto.Frontmatter;
import com.knowledge.platform.content.model.dto.InlineContent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns raw Markdown into a {@link CanonicalDocument}.
 *
 * <p>Parsing and interpretation are separate steps ({@link MarkdownParser} then
 * {@link DocumentConverter}); this composes them and applies the document-level rules that need both
 * -- where a title comes from, and what an article is about when the author did not say.
 */
@Component
public class CanonicalDocumentFactory {

    /** Frontmatter keys, matched case-insensitively by {@link Frontmatter}. */
    public static final String TITLE_KEY = "title";
    public static final String SUMMARY_KEY = "summary";
    public static final String DESCRIPTION_KEY = "description";

    /** Roughly a short paragraph; long enough to be useful in a search result, short enough to scan. */
    private static final int DERIVED_SUMMARY_MAX_LENGTH = 280;

    private final MarkdownParser markdownParser;
    private final DocumentConverter documentConverter;

    public CanonicalDocumentFactory(MarkdownParser markdownParser, DocumentConverter documentConverter) {
        this.markdownParser = markdownParser;
        this.documentConverter = documentConverter;
    }

    public CanonicalDocument create(String markdown) {
        MarkdownParser.ParsedMarkdown parsed = markdownParser.parse(markdown);
        DocumentConverter.ConversionResult converted = documentConverter.convert(parsed.document());
        Frontmatter frontmatter = parsed.frontmatter();

        return new CanonicalDocument(
                resolveTitle(frontmatter, converted.outline()),
                resolveSummary(frontmatter, converted.blocks()),
                frontmatter,
                converted.blocks(),
                converted.outline(),
                converted.links(),
                converted.plainText(),
                converted.wordCount(),
                converted.readingTimeMinutes(),
                converted.droppedRawHtml());
    }

    /**
     * Title precedence: frontmatter, then the first level-1 heading, then the first heading of any
     * level.
     *
     * <p>Frontmatter wins because it is the author's explicit statement. The heading fallback exists
     * because a great many real Markdown files carry no frontmatter at all, and rejecting them would
     * make connecting an existing repository a rewriting exercise. Returning null here is legitimate;
     * the ingestion validation handler decides whether a titleless document is an error.
     */
    private String resolveTitle(Frontmatter frontmatter, List<DocumentHeading> outline) {
        return frontmatter.first(TITLE_KEY)
                .or(() -> outline.stream()
                        .filter(heading -> heading.level() == 1)
                        .map(DocumentHeading::text)
                        .findFirst())
                .or(() -> outline.stream().map(DocumentHeading::text).findFirst())
                .orElse(null);
    }

    /**
     * Summary precedence: frontmatter {@code summary}, then {@code description}, then the first
     * paragraph truncated on a word boundary.
     */
    private String resolveSummary(Frontmatter frontmatter, List<ContentBlock> blocks) {
        return frontmatter.first(SUMMARY_KEY)
                .or(() -> frontmatter.first(DESCRIPTION_KEY))
                .orElseGet(() -> firstParagraphSummary(blocks));
    }

    private String firstParagraphSummary(List<ContentBlock> blocks) {
        return blocks.stream()
                .filter(ContentBlock.Paragraph.class::isInstance)
                .map(ContentBlock.Paragraph.class::cast)
                .map(this::flatten)
                .filter(text -> !text.isBlank())
                .findFirst()
                .map(this::truncateOnWordBoundary)
                .orElse(null);
    }

    private String flatten(ContentBlock.Paragraph paragraph) {
        StringBuilder builder = new StringBuilder();
        flattenInto(paragraph.content(), builder);
        return builder.toString().strip();
    }

    private void flattenInto(
            List<InlineContent> content, StringBuilder builder) {
        for (var inline : content) {
            switch (inline) {
                case InlineContent.TextRun text ->
                        builder.append(text.text());
                case InlineContent.InlineCode code ->
                        builder.append(code.code());
                case InlineContent.StyledRun styled ->
                        flattenInto(styled.children(), builder);
                case InlineContent.ExternalLink link ->
                        flattenInto(link.children(), builder);
                case InlineContent.InternalLink link ->
                        builder.append(link.displayText());
                case InlineContent.LineBreak ignored ->
                        builder.append(' ');
                case InlineContent.ImageReference ignored -> {
                    // An image's alt text describes the image, not the article; including it in a
                    // summary produces "Diagram of the request path" as the article's description.
                }
            }
        }
    }

    private String truncateOnWordBoundary(String text) {
        if (text.length() <= DERIVED_SUMMARY_MAX_LENGTH) {
            return text;
        }
        String clipped = text.substring(0, DERIVED_SUMMARY_MAX_LENGTH);
        int lastSpace = clipped.lastIndexOf(' ');
        return (lastSpace > 0 ? clipped.substring(0, lastSpace) : clipped).stripTrailing() + "…";
    }
}

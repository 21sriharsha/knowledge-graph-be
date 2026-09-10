package com.knowledge.platform.delivery.service.assembler;

import com.knowledge.platform.content.model.dto.ContentBlock;
import com.knowledge.platform.content.model.dto.InlineContent;
import com.knowledge.platform.content.model.entity.Article;
import java.util.List;
import java.util.Map;

/**
 * Rewrites the block tree so every internal link carries its true resolution state.
 *
 * <p>The content module produces links marked unresolved, because it genuinely does not know: only
 * the graph module knows which slugs exist. Rather than leaking that knowledge into content parsing,
 * the delivery module patches the tree at assembly time, which keeps the module boundary intact and
 * keeps the parse output a pure function of the Markdown.
 *
 * <p>Rebuilds rather than mutates -- the blocks are immutable records, and that immutability is what
 * makes them safe to cache and share between requests.
 */
final class InternalLinkResolutionVisitor {

    private InternalLinkResolutionVisitor() {
    }

    static List<ContentBlock> applyTo(
            List<ContentBlock> blocks, Map<String, Boolean> resolutionBySlug, Article article) {
        return blocks.stream().map(block -> apply(block, resolutionBySlug, article)).toList();
    }

    private static ContentBlock apply(ContentBlock block, Map<String, Boolean> resolution, Article article) {
        return switch (block) {
            case ContentBlock.Heading heading -> new ContentBlock.Heading(
                    heading.level(), heading.anchorId(), heading.text(),
                    applyInline(heading.content(), resolution, article));
            case ContentBlock.Paragraph paragraph ->
                    new ContentBlock.Paragraph(applyInline(paragraph.content(), resolution, article));
            case ContentBlock.Quote quote -> new ContentBlock.Quote(applyTo(quote.content(), resolution, article));
            case ContentBlock.ListBlock list -> new ContentBlock.ListBlock(
                    list.ordered(), list.startNumber(),
                    list.items().stream()
                            .map(item -> new ContentBlock.ListItem(applyTo(item.content(), resolution, article)))
                            .toList());
            case ContentBlock.TableBlock table -> new ContentBlock.TableBlock(
                    applyRows(table.header(), resolution, article), applyRows(table.rows(), resolution, article));
            // Code blocks and thematic breaks contain no inline content, so they pass through.
            case ContentBlock.CodeBlock code -> code;
            case ContentBlock.ThematicBreak thematicBreak -> thematicBreak;
        };
    }

    private static List<ContentBlock.TableRow> applyRows(
            List<ContentBlock.TableRow> rows, Map<String, Boolean> resolution, Article article) {
        return rows.stream()
                .map(row -> new ContentBlock.TableRow(row.cells().stream()
                        .map(cell -> new ContentBlock.TableCell(
                                applyInline(cell.content(), resolution, article), cell.alignment()))
                        .toList()))
                .toList();
    }

    private static List<InlineContent> applyInline(
            List<InlineContent> content, Map<String, Boolean> resolution, Article article) {
        return content.stream().map(inline -> switch (inline) {
            case InlineContent.InternalLink link -> new InlineContent.InternalLink(
                    link.targetReference(), link.targetSlug(), link.displayText(),
                    resolution.getOrDefault(link.targetSlug(), false));
            case InlineContent.StyledRun styled -> new InlineContent.StyledRun(
                    styled.style(), applyInline(styled.children(), resolution, article));
            case InlineContent.ExternalLink link -> new InlineContent.ExternalLink(
                    link.url(), link.title(), applyInline(link.children(), resolution, article));
            case InlineContent.ImageReference image -> new InlineContent.ImageReference(
                    ImageSourceResolver.resolve(image.url(), article), image.altText(),
                    image.title());
            default -> inline;
        }).toList();
    }
}

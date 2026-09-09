package com.knowledge.platform.content.markdown;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.common.service.SlugPolicy;
import com.knowledge.platform.content.model.dto.ContentBlock;
import com.knowledge.platform.content.model.dto.DocumentHeading;
import com.knowledge.platform.content.model.dto.ExtractedLink;
import com.knowledge.platform.content.model.dto.InlineContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.springframework.stereotype.Component;

/**
 * Converts a commonmark AST into the platform's own structured representation.
 *
 * <p>One traversal produces everything downstream needs -- the block tree for the read model, the
 * heading outline for navigation, the internal links for the graph, and the plain text for search
 * and embeddings. Walking once rather than four times is not just cheaper; it guarantees the four
 * outputs describe the same document, which four independent visitors would not.
 *
 * <p>Raw HTML is dropped rather than passed through. The backend does not emit HTML, and forwarding
 * unsanitised markup would quietly promote the frontend into a security boundary it is explicitly
 * not. What was dropped is reported so ingestion can tell the author.
 */
@Component
public class DocumentConverter {

    /** Average adult reading speed for technical prose, used for the reading-time estimate. */
    private static final int WORDS_PER_MINUTE = 220;

    private final SlugPolicy slugPolicy;

    public DocumentConverter(SlugPolicy slugPolicy) {
        this.slugPolicy = slugPolicy;
    }

    public ConversionResult convert(Node document) {
        Conversion conversion = new Conversion();
        List<ContentBlock> blocks = conversion.blocks(document);
        String plainText = conversion.plainText.toString().strip();
        int wordCount = countWords(plainText);
        return new ConversionResult(
                blocks,
                List.copyOf(conversion.outline),
                List.copyOf(conversion.links),
                plainText,
                wordCount,
                Math.max(1, (int) Math.ceil((double) wordCount / WORDS_PER_MINUTE)),
                List.copyOf(conversion.droppedRawHtml));
    }

    private int countWords(String text) {
        if (text.isBlank()) {
            return 0;
        }
        return text.split("\\s+").length;
    }

    /** Everything a single traversal yields. */
    public record ConversionResult(
            List<ContentBlock> blocks,
            List<DocumentHeading> outline,
            List<ExtractedLink> links,
            String plainText,
            int wordCount,
            int readingTimeMinutes,
            List<String> droppedRawHtml) {
    }

    /**
     * Per-document mutable state.
     *
     * <p>An inner class rather than fields on the bean: {@link DocumentConverter} is a singleton and
     * two ingestion threads convert documents concurrently. Anchor counters and link ordinals living
     * on the bean would interleave between documents.
     */
    private final class Conversion {

        private final StringBuilder plainText = new StringBuilder();
        private final List<DocumentHeading> outline = new ArrayList<>();
        private final List<ExtractedLink> links = new ArrayList<>();
        private final List<String> droppedRawHtml = new ArrayList<>();
        private final Map<String, Integer> anchorCounts = new HashMap<>();
        private int linkOrdinal;

        private List<ContentBlock> blocks(Node parent) {
            List<ContentBlock> result = new ArrayList<>();
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                ContentBlock block = block(child);
                if (block != null) {
                    result.add(block);
                }
            }
            return result;
        }

        private ContentBlock block(Node node) {
            return switch (node) {
                case Heading heading -> heading(heading);
                case Paragraph paragraph -> new ContentBlock.Paragraph(inlines(paragraph));
                case FencedCodeBlock code -> codeBlock(code.getInfo(), code.getLiteral());
                case IndentedCodeBlock code -> codeBlock(null, code.getLiteral());
                case BlockQuote quote -> new ContentBlock.Quote(blocks(quote));
                case BulletList list -> new ContentBlock.ListBlock(false, null, listItems(list));
                case OrderedList list ->
                        new ContentBlock.ListBlock(true, list.getMarkerStartNumber(), listItems(list));
                case org.commonmark.ext.gfm.tables.TableBlock table -> table(table);
                case ThematicBreak ignored -> new ContentBlock.ThematicBreak();
                case HtmlBlock html -> {
                    droppedRawHtml.add(html.getLiteral());
                    yield null;
                }
                // Frontmatter is lifted out separately, and a link reference definition is metadata
                // consumed by the parser rather than content. Both are correctly invisible here.
                case LinkReferenceDefinition ignored -> null;
                default -> null;
            };
        }

        private ContentBlock heading(Heading heading) {
            List<InlineContent> content = inlines(heading);
            String text = textOf(heading);
            String anchorId = uniqueAnchor(text);
            outline.add(new DocumentHeading(heading.getLevel(), anchorId, text));
            return new ContentBlock.Heading(heading.getLevel(), anchorId, text, content);
        }

        private ContentBlock codeBlock(String info, String literal) {
            String language = info == null || info.isBlank() ? null : info.trim().split("\\s+")[0];
            // Code contributes to the plain text: an article's identifiers and API names are often
            // exactly what a reader searches for, and excluding them would make code-heavy articles
            // unfindable by their own subject matter.
            appendPlainText(literal);
            return new ContentBlock.CodeBlock(language, literal);
        }

        private List<ContentBlock.ListItem> listItems(Node list) {
            List<ContentBlock.ListItem> items = new ArrayList<>();
            for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    items.add(new ContentBlock.ListItem(blocks(child)));
                }
            }
            return items;
        }

        private ContentBlock table(org.commonmark.ext.gfm.tables.TableBlock table) {
            List<ContentBlock.TableRow> header = new ArrayList<>();
            List<ContentBlock.TableRow> body = new ArrayList<>();
            for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
                List<ContentBlock.TableRow> target =
                        section instanceof TableHead ? header : section instanceof TableBody ? body : null;
                if (target == null) {
                    continue;
                }
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (row instanceof TableRow) {
                        target.add(new ContentBlock.TableRow(tableCells(row)));
                    }
                }
            }
            return new ContentBlock.TableBlock(header, body);
        }

        private List<ContentBlock.TableCell> tableCells(Node row) {
            List<ContentBlock.TableCell> cells = new ArrayList<>();
            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                if (cell instanceof TableCell tableCell) {
                    String alignment = tableCell.getAlignment() == null
                            ? null
                            : tableCell.getAlignment().name().toLowerCase(Locale.ROOT);
                    cells.add(new ContentBlock.TableCell(inlines(tableCell), alignment));
                }
            }
            return cells;
        }

        private List<InlineContent> inlines(Node parent) {
            List<InlineContent> result = new ArrayList<>();
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                InlineContent inline = inline(child);
                if (inline != null) {
                    result.add(inline);
                }
            }
            return result;
        }

        private InlineContent inline(Node node) {
            return switch (node) {
                case Text text -> {
                    appendPlainText(text.getLiteral());
                    yield new InlineContent.TextRun(text.getLiteral());
                }
                case Emphasis emphasis ->
                        new InlineContent.StyledRun(InlineContent.Style.EMPHASIS, inlines(emphasis));
                case StrongEmphasis strong ->
                        new InlineContent.StyledRun(InlineContent.Style.STRONG, inlines(strong));
                case Code code -> {
                    appendPlainText(code.getLiteral());
                    yield new InlineContent.InlineCode(code.getLiteral());
                }
                case WikiLinkNode wikiLink -> wikiLink(wikiLink);
                case Link link -> link(link);
                case Image image -> {
                    appendPlainText(textOf(image));
                    yield new InlineContent.ImageReference(
                            image.getDestination(), textOf(image), image.getTitle());
                }
                case SoftLineBreak ignored -> {
                    appendPlainText(" ");
                    yield new InlineContent.LineBreak(false);
                }
                case HardLineBreak ignored -> {
                    appendPlainText(" ");
                    yield new InlineContent.LineBreak(true);
                }
                case HtmlInline html -> {
                    droppedRawHtml.add(html.getLiteral());
                    yield null;
                }
                default -> null;
            };
        }

        private InlineContent wikiLink(WikiLinkNode node) {
            Slug targetSlug = slugPolicy.slugifyOrNull(node.getTarget());
            if (targetSlug == null) {
                // A reference that normalizes to nothing ("[[ ... ]]") can never resolve. Keeping the
                // author's text is more useful than emitting a permanently broken link.
                appendPlainText(node.getDisplayText());
                return new InlineContent.TextRun(node.getDisplayText());
            }
            links.add(new ExtractedLink(
                    node.getTarget(),
                    targetSlug.value(),
                    node.getDisplayText(),
                    ExtractedLink.LinkType.INTERNAL_WIKI,
                    linkOrdinal++));
            appendPlainText(node.getDisplayText());
            // resolved is false here by construction: the content module extracts links, and only the
            // graph module knows which targets exist. The read-model assembler fills this in.
            return new InlineContent.InternalLink(
                    node.getTarget(), targetSlug.value(), node.getDisplayText(), false);
        }

        private InlineContent link(Link link) {
            String destination = link.getDestination();
            List<InlineContent> children = inlines(link);
            Slug internalSlug = internalSlugFor(destination);
            if (internalSlug == null) {
                return new InlineContent.ExternalLink(destination, link.getTitle(), children);
            }
            String displayText = textOf(link);
            links.add(new ExtractedLink(
                    destination,
                    internalSlug.value(),
                    displayText,
                    ExtractedLink.LinkType.INTERNAL_SLUG,
                    linkOrdinal++));
            return new InlineContent.InternalLink(
                    destination, internalSlug.value(), displayText, false);
        }

        /**
         * Decides whether a Markdown link points inside the platform.
         *
         * <p>Anything with a URI scheme is external, including {@code mailto:} and {@code //host}.
         * A same-page fragment is navigation, not a graph edge. What remains is a relative or
         * root-relative path, whose last segment is the slug -- so {@code ./postgres-indexes.md} and
         * {@code /articles/postgres-indexes} both resolve to the same article.
         */
        private Slug internalSlugFor(String destination) {
            if (destination == null || destination.isBlank() || destination.startsWith("#")) {
                return null;
            }
            if (destination.contains("://") || destination.startsWith("//")
                    || destination.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
                return null;
            }
            String path = destination.split("[?#]")[0];
            int lastSlash = path.lastIndexOf('/');
            String lastSegment = lastSlash < 0 ? path : path.substring(lastSlash + 1);
            String withoutExtension = lastSegment.replaceAll("\\.(md|markdown)$", "");
            return slugPolicy.slugifyOrNull(withoutExtension);
        }

        /** Heading anchors must be unique within a document, or deep links become ambiguous. */
        private String uniqueAnchor(String text) {
            Slug base = slugPolicy.slugifyOrNull(text);
            String candidate = base == null ? "section" : base.value();
            int occurrence = anchorCounts.merge(candidate, 1, Integer::sum);
            return occurrence == 1 ? candidate : candidate + "-" + occurrence;
        }

        private void appendPlainText(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            plainText.append(text);
            if (!text.endsWith(" ") && !text.endsWith("\n")) {
                plainText.append(' ');
            }
        }

        /** Flattens an inline subtree to its text, without touching the accumulating plain text. */
        private String textOf(Node parent) {
            StringBuilder builder = new StringBuilder();
            collectText(parent, builder);
            return builder.toString().strip();
        }

        private void collectText(Node parent, StringBuilder builder) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                switch (child) {
                    case Text text -> builder.append(text.getLiteral());
                    case Code code -> builder.append(code.getLiteral());
                    case WikiLinkNode wikiLink -> builder.append(wikiLink.getDisplayText());
                    case SoftLineBreak ignored -> builder.append(' ');
                    case HardLineBreak ignored -> builder.append(' ');
                    default -> collectText(child, builder);
                }
            }
        }
    }
}

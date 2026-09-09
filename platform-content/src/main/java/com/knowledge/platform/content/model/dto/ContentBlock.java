package com.knowledge.platform.content.model.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * A block-level element of a canonical document.
 *
 * <p>Together with {@link InlineContent} this is the article body as the frontend receives it:
 * structured JSON, not HTML. It preserves everything the North Star's Markdown section requires --
 * headings, paragraphs, code blocks, links, internal links, lists, tables, images -- while leaving
 * every rendering decision to the client.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentBlock.Heading.class, name = "heading"),
        @JsonSubTypes.Type(value = ContentBlock.Paragraph.class, name = "paragraph"),
        @JsonSubTypes.Type(value = ContentBlock.CodeBlock.class, name = "code"),
        @JsonSubTypes.Type(value = ContentBlock.Quote.class, name = "quote"),
        @JsonSubTypes.Type(value = ContentBlock.ListBlock.class, name = "list"),
        @JsonSubTypes.Type(value = ContentBlock.TableBlock.class, name = "table"),
        @JsonSubTypes.Type(value = ContentBlock.ThematicBreak.class, name = "thematicBreak")
})
public sealed interface ContentBlock {

    /**
     * A section heading.
     *
     * <p>{@code anchorId} is generated during conversion rather than by the frontend, so that a
     * deep link into a section is stable across clients and across a rendering rewrite.
     */
    record Heading(int level, String anchorId, String text, List<InlineContent> content)
            implements ContentBlock {
        public Heading {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    record Paragraph(List<InlineContent> content) implements ContentBlock {
        public Paragraph {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    /** A fenced or indented code block. {@code language} is null when the fence declared none. */
    record CodeBlock(String language, String code) implements ContentBlock {
    }

    record Quote(List<ContentBlock> content) implements ContentBlock {
        public Quote {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    /** An ordered or bullet list. Items hold blocks, so nesting works without a special case. */
    record ListBlock(boolean ordered, Integer startNumber, List<ListItem> items)
            implements ContentBlock {
        public ListBlock {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ListItem(List<ContentBlock> content) {
        public ListItem {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    record TableBlock(List<TableRow> header, List<TableRow> rows) implements ContentBlock {
        public TableBlock {
            header = header == null ? List.of() : List.copyOf(header);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    record TableRow(List<TableCell> cells) {
        public TableRow {
            cells = cells == null ? List.of() : List.copyOf(cells);
        }
    }

    record TableCell(List<InlineContent> content, String alignment) {
        public TableCell {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    record ThematicBreak() implements ContentBlock {
    }
}

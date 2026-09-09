package com.knowledge.platform.content.markdown;

import com.knowledge.platform.content.model.dto.Frontmatter;
import java.util.List;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * Parses canonical Markdown into a commonmark AST plus its frontmatter.
 *
 * <p>The configured parser is the platform's definition of what Markdown means here: CommonMark,
 * plus GFM tables, plus YAML frontmatter, plus internal {@code [[wiki links]]}. Building it once and
 * sharing it is safe -- commonmark's {@code Parser} is immutable and thread-safe after construction,
 * and its per-parse state lives in the returned node tree.
 */
@Component
public class MarkdownParser {

    private final Parser parser;

    public MarkdownParser() {
        this.parser = Parser.builder()
                .extensions(List.of(
                        YamlFrontMatterExtension.create(),
                        TablesExtension.create(),
                        WikiLinkExtension.create()))
                .build();
    }

    /**
     * Parses a document.
     *
     * @throws IllegalArgumentException when the input is null
     */
    public ParsedMarkdown parse(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("markdown must not be null");
        }

        Node document = parser.parse(markdown);

        YamlFrontMatterVisitor frontmatterVisitor = new YamlFrontMatterVisitor();
        document.accept(frontmatterVisitor);

        return new ParsedMarkdown(document, new Frontmatter(frontmatterVisitor.getData()));
    }

    /**
     * A parsed document: the AST, and the frontmatter lifted out of it.
     *
     * <p>Deliberately shallow. Turning this into a {@link
     * com.knowledge.platform.content.model.dto.CanonicalDocument} is
     * {@link CanonicalDocumentFactory}'s job, so that parsing and interpretation stay separately
     * testable.
     */
    public record ParsedMarkdown(Node document, Frontmatter frontmatter) {
    }
}

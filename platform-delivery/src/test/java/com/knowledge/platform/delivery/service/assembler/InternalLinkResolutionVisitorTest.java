package com.knowledge.platform.delivery.service.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.common.service.DefaultSlugPolicyImpl;
import com.knowledge.platform.content.markdown.CanonicalDocumentFactory;
import com.knowledge.platform.content.markdown.DocumentConverter;
import com.knowledge.platform.content.markdown.MarkdownParser;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.model.dto.ContentBlock;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.dto.InlineContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The content module parses links without knowing which targets exist; only the graph module knows
 * that. This visitor is where the two are joined, and the property under test is that it reaches
 * every link in the tree, however deeply nested.
 */
class InternalLinkResolutionVisitorTest {

    private final CanonicalDocumentFactory factory = new CanonicalDocumentFactory(
            new MarkdownParser(), new DocumentConverter(new DefaultSlugPolicyImpl()));

    @Test
    @DisplayName("links parse as unresolved, because the content module cannot know otherwise")
    void parsedLinksStartUnresolved() {
        CanonicalDocument document = factory.create("See [[Deployment]].");

        assertThat(internalLinks(document.blocks()))
                .singleElement()
                .extracting(InlineContent.InternalLink::resolved)
                .isEqualTo(false);
    }

    @Test
    void marksLinksResolvedWhenTheirTargetExists() {
        CanonicalDocument document = factory.create("See [[Deployment]] and [[Missing Page]].");

        List<ContentBlock> patched = InternalLinkResolutionVisitor.applyTo(
                document.blocks(), Map.of("deployment", true, "missing-page", false), articleWithoutRepository());

        assertThat(internalLinks(patched))
                .extracting(InlineContent.InternalLink::targetSlug,
                        InlineContent.InternalLink::resolved)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("deployment", true),
                        org.assertj.core.groups.Tuple.tuple("missing-page", false));
    }

    @Test
    @DisplayName("links nested in emphasis, lists, quotes and tables are reached too")
    void reachesLinksAtEveryDepth() {
        CanonicalDocument document = factory.create("""
                Top level [[Deployment]].

                - a list item with [[Deployment]]
                - **bold containing [[Deployment]]**

                > a quote with [[Deployment]]

                | column |
                | ------ |
                | [[Deployment]] |
                """);

        List<ContentBlock> patched = InternalLinkResolutionVisitor.applyTo(
                document.blocks(), Map.of("deployment", true), articleWithoutRepository());

        List<InlineContent.InternalLink> links = internalLinks(patched);
        assertThat(links).hasSize(5);
        assertThat(links).allMatch(InlineContent.InternalLink::resolved);
    }

    @Test
    @DisplayName("a slug the graph said nothing about defaults to unresolved, never to resolved")
    void defaultsToUnresolvedForUnknownSlugs() {
        CanonicalDocument document = factory.create("See [[Deployment]].");

        List<ContentBlock> patched =
                InternalLinkResolutionVisitor.applyTo(document.blocks(), Map.of(), articleWithoutRepository());

        assertThat(internalLinks(patched)).singleElement()
                .extracting(InlineContent.InternalLink::resolved).isEqualTo(false);
    }

    @Test
    @DisplayName("blocks with no inline content pass through untouched")
    void leavesCodeAndBreaksAlone() {
        CanonicalDocument document = factory.create("```java\nint x = 1;\n```\n\n---\n");

        List<ContentBlock> patched =
                InternalLinkResolutionVisitor.applyTo(document.blocks(), Map.of(), articleWithoutRepository());

        assertThat(patched).hasSize(2);
        assertThat(patched.get(0)).isInstanceOf(ContentBlock.CodeBlock.class);
        assertThat(patched.get(1)).isInstanceOf(ContentBlock.ThematicBreak.class);
    }

    private List<InlineContent.InternalLink> internalLinks(List<ContentBlock> blocks) {
        List<InlineContent.InternalLink> found = new ArrayList<>();
        blocks.forEach(block -> collectFromBlock(block, found));
        return found;
    }

    private void collectFromBlock(ContentBlock block, List<InlineContent.InternalLink> found) {
        switch (block) {
            case ContentBlock.Heading heading -> collectFromInline(heading.content(), found);
            case ContentBlock.Paragraph paragraph -> collectFromInline(paragraph.content(), found);
            case ContentBlock.Quote quote -> quote.content().forEach(b -> collectFromBlock(b, found));
            case ContentBlock.ListBlock list -> list.items()
                    .forEach(item -> item.content().forEach(b -> collectFromBlock(b, found)));
            case ContentBlock.TableBlock table -> {
                table.header().forEach(row -> row.cells()
                        .forEach(cell -> collectFromInline(cell.content(), found)));
                table.rows().forEach(row -> row.cells()
                        .forEach(cell -> collectFromInline(cell.content(), found)));
            }
            case ContentBlock.CodeBlock ignored -> { }
            case ContentBlock.ThematicBreak ignored -> { }
        }
    }

    private void collectFromInline(
            List<InlineContent> content, List<InlineContent.InternalLink> found) {
        for (InlineContent inline : content) {
            switch (inline) {
                case InlineContent.InternalLink link -> found.add(link);
                case InlineContent.StyledRun styled -> collectFromInline(styled.children(), found);
                case InlineContent.ExternalLink link -> collectFromInline(link.children(), found);
                default -> { }
            }
        }
    }

    /**
     * These tests are about link resolution, not images.
     *
     * <p>An article with no repository resolves every relative image to null, which keeps image
     * handling out of what they assert while still exercising the same walk.
     */
    private static Article articleWithoutRepository() {
        Article article = org.mockito.Mockito.mock(Article.class);
        org.mockito.Mockito.when(article.getRepositoryId()).thenReturn(null);
        return article;
    }
}

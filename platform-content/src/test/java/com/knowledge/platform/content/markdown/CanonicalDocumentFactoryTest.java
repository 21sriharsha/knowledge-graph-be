package com.knowledge.platform.content.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.common.service.DefaultSlugPolicyImpl;
import com.knowledge.platform.common.service.SlugPolicy;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.model.dto.ContentBlock;
import com.knowledge.platform.content.model.dto.ExtractedLink;
import com.knowledge.platform.content.model.dto.InlineContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the Markdown pipeline: what an author writes, and what the platform derives from it.
 */
class CanonicalDocumentFactoryTest {

    private final SlugPolicy slugPolicy = new DefaultSlugPolicyImpl();
    private final CanonicalDocumentFactory factory =
            new CanonicalDocumentFactory(new MarkdownParser(), new DocumentConverter(slugPolicy));

    @Nested
    @DisplayName("internal links")
    class InternalLinks {

        @Test
        void extractsPlainWikiLink() {
            CanonicalDocument document = factory.create("See [[PostgreSQL Indexes]] for detail.");

            assertThat(document.links()).singleElement().satisfies(link -> {
                assertThat(link.targetReference()).isEqualTo("PostgreSQL Indexes");
                assertThat(link.targetSlug()).isEqualTo("postgresql-indexes");
                assertThat(link.displayText()).isEqualTo("PostgreSQL Indexes");
                assertThat(link.type()).isEqualTo(ExtractedLink.LinkType.INTERNAL_WIKI);
            });
        }

        @Test
        void extractsAliasedWikiLink() {
            CanonicalDocument document = factory.create("See [[PostgreSQL Indexes|the index guide]].");

            assertThat(document.links()).singleElement().satisfies(link -> {
                assertThat(link.targetSlug()).isEqualTo("postgresql-indexes");
                assertThat(link.displayText()).isEqualTo("the index guide");
            });
        }

        @Test
        @DisplayName("a reference inside a code fence is an example, not a link")
        void ignoresWikiLinkInsideFencedCode() {
            CanonicalDocument document = factory.create("""
                    Write a reference like this:

                    ```markdown
                    [[Kubernetes Networking]]
                    ```
                    """);

            assertThat(document.links()).isEmpty();
        }

        @Test
        @DisplayName("a reference inside an inline code span is an example, not a link")
        void ignoresWikiLinkInsideInlineCode() {
            CanonicalDocument document = factory.create("Use `[[Target]]` to link.");

            assertThat(document.links()).isEmpty();
        }

        @Test
        void treatsUnterminatedReferenceAsLiteralText() {
            CanonicalDocument document = factory.create("An unclosed [[reference stays text.");

            assertThat(document.links()).isEmpty();
            assertThat(document.plainText()).contains("[[reference stays text.");
        }

        @Test
        void normalizesCaseAndPunctuationToTheSameSlug() {
            CanonicalDocument document =
                    factory.create("[[Kubernetes Networking]] and [[kubernetes  networking]]");

            assertThat(document.links())
                    .extracting(ExtractedLink::targetSlug)
                    .containsExactly("kubernetes-networking", "kubernetes-networking");
        }

        @Test
        void distinguishesRepeatedReferencesByOrdinal() {
            CanonicalDocument document = factory.create("[[A]] then [[B]] then [[A]]");

            assertThat(document.links()).extracting(ExtractedLink::ordinal).containsExactly(0, 1, 2);
        }

        @Test
        void treatsRelativeMarkdownLinkAsInternal() {
            CanonicalDocument document = factory.create("See [indexes](./postgresql-indexes.md).");

            assertThat(document.links()).singleElement().satisfies(link -> {
                assertThat(link.targetSlug()).isEqualTo("postgresql-indexes");
                assertThat(link.type()).isEqualTo(ExtractedLink.LinkType.INTERNAL_SLUG);
            });
        }

        @Test
        void treatsAbsoluteUrlAsExternal() {
            CanonicalDocument document = factory.create("See [docs](https://example.com/indexes).");

            assertThat(document.links()).isEmpty();
            assertThat(firstParagraph(document).content())
                    .hasAtLeastOneElementOfType(InlineContent.ExternalLink.class);
        }

        @Test
        void treatsSamePageFragmentAsNavigationNotAnEdge() {
            CanonicalDocument document = factory.create("Jump to [the section](#background).");

            assertThat(document.links()).isEmpty();
        }
    }

    @Nested
    @DisplayName("frontmatter and derived metadata")
    class Metadata {

        @Test
        void prefersFrontmatterTitleOverHeading() {
            CanonicalDocument document = factory.create("""
                    ---
                    title: Canonical Title
                    tags: postgres, indexing
                    ---

                    # Heading Title

                    Body text.
                    """);

            assertThat(document.title()).isEqualTo("Canonical Title");
            assertThat(document.frontmatter().all("tags")).containsExactly("postgres", "indexing");
        }

        @Test
        void fallsBackToFirstHeadingWhenFrontmatterHasNoTitle() {
            CanonicalDocument document = factory.create("# Heading Title\n\nBody text.");

            assertThat(document.title()).isEqualTo("Heading Title");
        }

        @Test
        void derivesSummaryFromFirstParagraphWhenNoneDeclared() {
            CanonicalDocument document =
                    factory.create("# Title\n\nThe first paragraph explains the article.\n\nMore.");

            assertThat(document.summary()).isEqualTo("The first paragraph explains the article.");
        }

        @Test
        void buildsUniqueAnchorsForRepeatedHeadings() {
            CanonicalDocument document = factory.create("""
                    ## Setup

                    text

                    ## Setup

                    text
                    """);

            assertThat(document.outline())
                    .extracting(heading -> heading.anchorId())
                    .containsExactly("setup", "setup-2");
        }

        @Test
        void countsWordsAndEstimatesReadingTime() {
            // 300 body words plus the title, at 220 words per minute, rounds up to two minutes.
            CanonicalDocument document = factory.create("# Title\n\n" + "word ".repeat(300));

            assertThat(document.wordCount()).isEqualTo(301);
            assertThat(document.readingTimeMinutes()).isEqualTo(2);
        }

        @Test
        void reportsAtLeastOneMinuteForAShortArticle() {
            CanonicalDocument document = factory.create("# Title\n\nA short note.");

            assertThat(document.readingTimeMinutes()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("structure and safety")
    class Structure {

        @Test
        void dropsRawHtmlAndReportsIt() {
            CanonicalDocument document =
                    factory.create("Text.\n\n<script>alert('xss')</script>\n\nMore text.");

            assertThat(document.hasDroppedRawHtml()).isTrue();
            assertThat(document.blocks()).noneMatch(block -> block.toString().contains("script"));
        }

        @Test
        void preservesCodeBlockLanguage() {
            CanonicalDocument document = factory.create("```java\nint x = 1;\n```");

            assertThat(document.blocks()).singleElement()
                    .isInstanceOfSatisfying(ContentBlock.CodeBlock.class, code -> {
                        assertThat(code.language()).isEqualTo("java");
                        assertThat(code.code()).contains("int x = 1;");
                    });
        }

        @Test
        void includesCodeContentInSearchableText() {
            CanonicalDocument document = factory.create("```java\nPreparedStatement statement;\n```");

            assertThat(document.plainText()).contains("PreparedStatement");
        }
    }

    private ContentBlock.Paragraph firstParagraph(CanonicalDocument document) {
        return document.blocks().stream()
                .filter(ContentBlock.Paragraph.class::isInstance)
                .map(ContentBlock.Paragraph.class::cast)
                .findFirst()
                .orElseThrow();
    }
}

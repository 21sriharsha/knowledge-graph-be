package com.knowledge.platform.delivery.service.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.content.model.entity.Article;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.Mockito;

/**
 * Where an author's image reference actually points.
 *
 * <p>Two things are being protected here. That a path written the way an author naturally writes it
 * resolves to something the platform can serve -- otherwise the image is broken on the site while
 * looking correct in the repository. And that no reference can address anything outside the
 * repository, because this runs at assembly time and a mistake would be baked into a stored read
 * model rather than caught on each request.
 */
class ImageSourceResolverTest {

    private static final UUID REPOSITORY = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Nested
    @DisplayName("repository-relative references")
    class RepositoryRelative {

        @Test
        @DisplayName("resolves beside the article")
        void resolvesSiblingPaths() {
            assertThat(resolve("diagram.png", "docs/hybrid-search.md"))
                    .isEqualTo("/api/assets/" + REPOSITORY + "/docs/diagram.png");
        }

        @Test
        @DisplayName("resolves an explicit ./ the same way")
        void resolvesDotSlash() {
            assertThat(resolve("./images/flow.png", "docs/hybrid-search.md"))
                    .isEqualTo("/api/assets/" + REPOSITORY + "/docs/images/flow.png");
        }

        @Test
        @DisplayName("climbs out of the content directory, which is a normal layout")
        void resolvesParentPaths() {
            // Images in assets/ beside docs/ rather than inside it is common enough that refusing
            // it would make the feature useless for the people who organise that way.
            assertThat(resolve("../assets/flow.png", "docs/hybrid-search.md"))
                    .isEqualTo("/api/assets/" + REPOSITORY + "/assets/flow.png");
        }

        @Test
        @DisplayName("a cache-busting suffix is not part of the path in the repository")
        void stripsQueryAndFragment() {
            assertThat(resolve("diagram.png?v=2", "docs/a.md"))
                    .isEqualTo("/api/assets/" + REPOSITORY + "/docs/diagram.png");
        }
    }

    @Nested
    @DisplayName("references that must not be rewritten")
    class LeftAlone {

        @Test
        @DisplayName("an absolute URL is the author's decision")
        void leavesAbsoluteUrls() {
            String url = "https://example.com/diagram.png";
            assertThat(resolve(url, "docs/a.md")).isEqualTo(url);
        }

        @Test
        @DisplayName("a data URI is already self-contained")
        void leavesDataUris() {
            String uri = "data:image/png;base64,iVBORw0KGgo=";
            assertThat(resolve(uri, "docs/a.md")).isEqualTo(uri);
        }

        @Test
        @DisplayName("a site-rooted path points at something we already serve")
        void leavesRootedPaths() {
            assertThat(resolve("/static/logo.png", "docs/a.md")).isEqualTo("/static/logo.png");
        }
    }

    @Nested
    @DisplayName("references that resolve to nothing")
    class Refused {

        @Test
        @DisplayName("cannot climb above the repository root")
        void refusesTraversalAboveRoot() {
            // The attack this exists to stop: reaching .git/config or a deploy key by walking up.
            assertThat(resolve("../../../../etc/passwd", "docs/a.md")).isNull();
            assertThat(resolve("../../secrets.png", "docs/a.md")).isNull();
        }

        @Test
        @DisplayName("an article with no repository has nowhere to resolve against")
        void refusesWhenTheArticleHasNoRepository() {
            Article article = Mockito.mock(Article.class);
            Mockito.when(article.getRepositoryId()).thenReturn(null);
            Mockito.when(article.getSourcePath()).thenReturn("docs/a.md");

            assertThat(ImageSourceResolver.resolve("diagram.png", article)).isNull();
        }

        @Test
        @DisplayName("an empty reference is not a path")
        void refusesBlankReferences() {
            assertThat(resolve("", "docs/a.md")).isNull();
            assertThat(resolve("   ", "docs/a.md")).isNull();
        }
    }

    private String resolve(String reference, String sourcePath) {
        Article article = Mockito.mock(Article.class);
        Mockito.when(article.getRepositoryId()).thenReturn(REPOSITORY);
        Mockito.when(article.getSourcePath()).thenReturn(sourcePath);
        return ImageSourceResolver.resolve(reference, article);
    }
}

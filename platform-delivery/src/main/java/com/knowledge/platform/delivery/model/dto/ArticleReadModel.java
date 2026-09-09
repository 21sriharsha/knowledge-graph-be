package com.knowledge.platform.delivery.model.dto;

import com.knowledge.platform.content.model.dto.ContentBlock;
import com.knowledge.platform.content.model.dto.DocumentHeading;
import com.knowledge.platform.graph.model.dto.GraphNeighbourhood;
import java.time.Instant;
import java.util.List;

/**
 * Everything the public article page needs, in one object.
 *
 * <p>The measure of this type is BACKEND-SPEC section 12: a read model must be sufficient for the
 * normal public article page with no follow-up backend calls. So the body, author, taxonomy, related
 * content, backlinks, outline, navigation and graph context are all here, assembled once when the
 * article changes rather than reconstructed on every request.
 *
 * <p>The body is a {@link ContentBlock} tree, not HTML. The backend does not generate markup; the
 * frontend maps these blocks onto its own components.
 *
 * @param unresolvedReferences references the author wrote whose targets do not exist. Surfaced rather
 *     than hidden, so the page can render them as a wiki does -- visibly missing -- instead of
 *     pretending the author never wrote them.
 */
public record ArticleReadModel(
        String slug,
        String title,
        String summary,
        List<ContentBlock> body,
        List<DocumentHeading> outline,
        AuthorSummaryModel author,
        List<TaxonomyReference> topics,
        List<TaxonomyReference> tags,
        List<ArticleReference> relatedArticles,
        List<ArticleReference> backlinks,
        List<String> unresolvedReferences,
        List<BreadcrumbEntry> breadcrumbs,
        ArticleReference previousArticle,
        ArticleReference nextArticle,
        GraphNeighbourhood graphContext,
        int wordCount,
        int readingTimeMinutes,
        Instant publishedAt,
        Instant updatedAt) {

    /**
     * The shape of this payload.
     *
     * <p>Persisted alongside every stored read model. Bump it whenever a field is added, removed or
     * given a different meaning here or in any nested type -- a payload written under an older
     * version is treated as stale and reassembled on next read. Without this, a shape change is
     * invisible to the staleness check, which only knows about the article's content hash, and old
     * payloads are served until the article itself happens to change.
     *
     * <p>2: graph nodes gained {@code topic}, so the graph can be grouped and coloured by subject.
     */
    public static final int SCHEMA_VERSION = 2;

    public ArticleReadModel {
        body = body == null ? List.of() : List.copyOf(body);
        outline = outline == null ? List.of() : List.copyOf(outline);
        topics = topics == null ? List.of() : List.copyOf(topics);
        tags = tags == null ? List.of() : List.copyOf(tags);
        relatedArticles = relatedArticles == null ? List.of() : List.copyOf(relatedArticles);
        backlinks = backlinks == null ? List.of() : List.copyOf(backlinks);
        unresolvedReferences = unresolvedReferences == null ? List.of() : List.copyOf(unresolvedReferences);
        breadcrumbs = breadcrumbs == null ? List.of() : List.copyOf(breadcrumbs);
    }

    /** The author, as an article page shows them. */
    public record AuthorSummaryModel(String slug, String displayName, String avatarUrl) {
    }

    /** A topic or tag, as a link. */
    public record TaxonomyReference(String slug, String name) {
    }

    /** One step of the navigation trail. */
    public record BreadcrumbEntry(String label, String path) {
    }
}

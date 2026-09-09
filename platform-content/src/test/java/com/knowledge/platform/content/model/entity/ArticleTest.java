package com.knowledge.platform.content.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The article carries the idempotency decision that the whole ingestion pipeline branches on, so its
 * behaviour around unchanged content is a contract rather than an implementation detail.
 */
class ArticleTest {

    private static final UUID AUTHOR = UUID.randomUUID();

    @Test
    @DisplayName("a new article carries its derived counts, not just its identity")
    void createsWithDerivedFields() {
        Article article = newArticle();

        assertThat(article.getTitle()).isEqualTo("Kubernetes Networking");
        assertThat(article.getSummary()).isEqualTo("How pods get addresses.");
        assertThat(article.getWordCount()).isEqualTo(1200);
        assertThat(article.getReadingTimeMinutes()).isEqualTo(6);
        assertThat(article.getRevisionNumber()).isEqualTo(1);
        assertThat(article.getPublicationState()).isEqualTo(PublicationState.DRAFT);
    }

    @Test
    @DisplayName("identical content is not a new revision -- this is what makes a replay cheap")
    void reportsNoChangeForIdenticalContent() {
        Article article = newArticle();

        boolean changed = article.applyRevision("Kubernetes Networking", "How pods get addresses.",
                "# Kubernetes Networking\n\nBody.", "hash-1", "rev-2", 1200, 6);

        assertThat(changed).isFalse();
        assertThat(article.getRevisionNumber()).isEqualTo(1);
        // The revision pointer still moves: a commit touching other files advances it, and recording
        // that keeps "which revision have we seen" accurate without rewriting derived state.
        assertThat(article.getSourceRevision()).isEqualTo("rev-2");
    }

    @Test
    void reportsChangeAndIncrementsTheRevisionForNewContent() {
        Article article = newArticle();

        boolean changed = article.applyRevision("Kubernetes Networking, Revisited", "New summary.",
                "# Revisited\n\nMore.", "hash-2", "rev-2", 1500, 7);

        assertThat(changed).isTrue();
        assertThat(article.getRevisionNumber()).isEqualTo(2);
        assertThat(article.getTitle()).isEqualTo("Kubernetes Networking, Revisited");
        assertThat(article.getSummary()).isEqualTo("New summary.");
        assertThat(article.getWordCount()).isEqualTo(1500);
    }

    @Test
    @DisplayName("publishedAt is set once, so re-publishing an edit does not reorder the archive")
    void doesNotMovePublishedAtOnRepublish() {
        Article article = newArticle();
        article.publish();
        var firstPublished = article.getPublishedAt();

        article.unpublish();
        article.publish();

        assertThat(article.getPublishedAt()).isEqualTo(firstPublished);
    }

    @Test
    void archivingLeavesTheArticleNonPublic() {
        Article article = newArticle();
        article.publish();
        article.archive();

        assertThat(article.getPublicationState()).isEqualTo(PublicationState.ARCHIVED);
        assertThat(article.getPublicationState().isPublic()).isFalse();
    }

    @Test
    @DisplayName("the primary topic is the first the author listed; frontmatter order is meaningful")
    void takesThePrimaryTopicFromTheFirstListed() {
        Article article = newArticle();
        Set<Topic> topics = new LinkedHashSet<>();
        Topic kubernetes = Topic.create("kubernetes", "Kubernetes");
        topics.add(kubernetes);
        topics.add(Topic.create("networking", "Networking"));

        article.replaceTopics(topics);

        assertThat(article.getPrimaryTopic()).isEqualTo(kubernetes);
        assertThat(article.getTopics()).hasSize(2);
    }

    @Test
    @DisplayName("taxonomy is replaced, not merged: a tag the author deleted must disappear")
    void replacesTaxonomyRatherThanMerging() {
        Article article = newArticle();
        article.replaceTags(Set.of(Tag.create("cni", "CNI"), Tag.create("legacy", "Legacy")));

        article.replaceTags(Set.of(Tag.create("cni", "CNI")));

        assertThat(article.getTags()).extracting(Tag::getSlug).containsExactly("cni");
    }

    @Test
    @DisplayName("renaming is allowed; the source coordinate, not the slug, is the stable identity")
    void supportsRenaming() {
        Article article = newArticle();
        article.bindToSource(UUID.randomUUID(), "docs/networking.md");

        article.rename("kubernetes-networking-v2");

        assertThat(article.getSlug()).isEqualTo("kubernetes-networking-v2");
        assertThat(article.getSourcePath()).isEqualTo("docs/networking.md");
    }

    @Test
    @DisplayName("hashCode is stable, so an entity does not get lost in a set when its id is assigned")
    void hasAStableHashCode() {
        Article article = newArticle();
        int before = article.hashCode();
        article.rename("something-else");

        assertThat(article.hashCode()).isEqualTo(before);
    }

    private Article newArticle() {
        return Article.create("kubernetes-networking", "Kubernetes Networking",
                "How pods get addresses.", "# Kubernetes Networking\n\nBody.", "hash-1",
                AUTHOR, 1200, 6);
    }
}

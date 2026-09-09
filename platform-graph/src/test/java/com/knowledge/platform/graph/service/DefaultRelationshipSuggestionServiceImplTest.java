package com.knowledge.platform.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import com.knowledge.platform.graph.model.entity.SuggestedRelationship;
import com.knowledge.platform.graph.repository.SuggestedRelationshipRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Suggestions are the platform's own guesses, so what matters is that they are explainable, bounded,
 * and never confusable with something an author wrote.
 */
@ExtendWith(MockitoExtension.class)
class DefaultRelationshipSuggestionServiceImplTest {

    @Mock
    private SuggestedRelationshipRepository repository;

    @Captor
    private ArgumentCaptor<List<SuggestedRelationship>> saved;

    private RelationshipSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultRelationshipSuggestionServiceImpl(
                repository, new GraphProperties(3, 150, 3));
    }

    @Test
    @DisplayName("articles sharing a topic are related; unrelated ones are not")
    void suggestsOnSharedTaxonomy() {
        Article subject = article("kubernetes-networking", Set.of("kubernetes"), Set.of("cni"));
        Article sibling = article("kubernetes-deployment", Set.of("kubernetes"), Set.of("cni"));
        Article stranger = article("postgres-indexes", Set.of("postgres"), Set.of("btree"));

        service.rebuildFor(subject, List.of(sibling, stranger));

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(SuggestedRelationship::getTargetArticleId)
                .containsExactly(sibling.getId());
    }

    @Test
    @DisplayName("a topic overlap is reported as such, because it tells the reader more than a tag")
    void prefersTheTopicExplanation() {
        Article subject = article("a", Set.of("kubernetes"), Set.of("cni"));
        Article sibling = article("b", Set.of("kubernetes"), Set.of("cni"));

        service.rebuildFor(subject, List.of(sibling));

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .extracting(SuggestedRelationship::getRelationshipKind)
                .isEqualTo(SuggestedRelationship.RelationshipKind.SHARED_TOPIC);
    }

    @Test
    void reportsATagOverlapWhenNoTopicIsShared() {
        Article subject = article("a", Set.of("kubernetes"), Set.of("cni"));
        Article sibling = article("b", Set.of("networking"), Set.of("cni"));

        service.rebuildFor(subject, List.of(sibling));

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .extracting(SuggestedRelationship::getRelationshipKind)
                .isEqualTo(SuggestedRelationship.RelationshipKind.SHARED_TAG);
    }

    @Test
    @DisplayName("one incidental shared tag is below the threshold and says nothing useful")
    void ignoresWeakOverlap() {
        Article subject = article("a", Set.of("t1", "t2", "t3", "t4"), Set.of("g1", "g2", "g3", "g4"));
        Article sibling = article("b", Set.of("t1"), Set.of("x1", "x2", "x3", "x4", "x5", "x6"));

        service.rebuildFor(subject, List.of(sibling));

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).isEmpty();
    }

    @Test
    @DisplayName("suggestions are capped, so a hub article does not relate to the whole corpus")
    void boundsTheNumberOfSuggestions() {
        Article subject = article("subject", Set.of("kubernetes"), Set.of("cni"));
        List<Article> candidates = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> article("sibling-" + i, Set.of("kubernetes"), Set.of("cni")))
                .toList();

        service.rebuildFor(subject, candidates);

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3); // maxSuggestionsPerArticle
    }

    @Test
    @DisplayName("suggestions are replaced, not merged: a retagged article loses what its tags implied")
    void replacesPreviousSuggestions() {
        Article subject = article("a", Set.of("kubernetes"), Set.of("cni"));

        service.rebuildFor(subject, List.of());

        verify(repository).deleteInvolving(subject.getId());
    }

    @Test
    void suggestsNothingForAnArticleWithNoTaxonomy() {
        Article subject = article("a", Set.of(), Set.of());

        int created = service.rebuildFor(subject, List.of(article("b", Set.of("k"), Set.of("c"))));

        assertThat(created).isZero();
    }

    @Test
    @DisplayName("an article is never related to itself")
    void excludesTheSubjectFromItsOwnCandidates() {
        Article subject = article("a", Set.of("kubernetes"), Set.of("cni"));

        service.rebuildFor(subject, List.of(subject));

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).isEmpty();
    }

    @Test
    @DisplayName("scores are comparable across articles, so a broadly tagged one cannot dominate")
    void scoresByProportionalOverlapNotRawCount() {
        Article subject = article("subject", Set.of("kubernetes"), Set.of("cni"));
        Article focused = article("focused", Set.of("kubernetes"), Set.of("cni"));
        Article broad = article("broad", Set.of("kubernetes"),
                Set.of("cni", "a", "b", "c", "d", "e", "f"));

        service.rebuildFor(subject, List.of(focused, broad));

        verify(repository).saveAll(saved.capture());
        // The focused article overlaps proportionally more, so it scores higher despite the broad one
        // matching the same two terms.
        assertThat(saved.getValue()).isSortedAccordingTo(
                (left, right) -> Double.compare(right.getScore(), left.getScore()));
        assertThat(saved.getValue().getFirst().getTargetArticleId()).isEqualTo(focused.getId());
    }

    private Article article(String slug, Set<String> topicSlugs, Set<String> tagSlugs) {
        Article article = Article.create(slug, "Title " + slug, "Summary", "# " + slug,
                "hash-" + slug, UUID.randomUUID(), 100, 1);
        Set<Topic> topics = new LinkedHashSet<>();
        topicSlugs.forEach(s -> topics.add(Topic.create(s, s)));
        article.replaceTopics(topics);
        Set<Tag> tags = new LinkedHashSet<>();
        tagSlugs.forEach(s -> tags.add(Tag.create(s, s)));
        article.replaceTags(tags);
        return article;
    }
}

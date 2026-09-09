package com.knowledge.platform.graph.service;

import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.graph.model.entity.SuggestedRelationship;
import com.knowledge.platform.graph.repository.SuggestedRelationshipRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default RelationshipSuggestionService.
 *
 * <p>See {@link RelationshipSuggestionService} for what this provides and why it exists.
 */
@Service
public class DefaultRelationshipSuggestionServiceImpl implements RelationshipSuggestionService {

    private static final String GENERATOR = "taxonomy-overlap";

    /** Below this, an overlap is one incidental shared tag and says nothing useful. */
    private static final double MINIMUM_SCORE = 0.15;

    private final SuggestedRelationshipRepository suggestionRepository;
    private final GraphProperties properties;

    public DefaultRelationshipSuggestionServiceImpl(
            SuggestedRelationshipRepository suggestionRepository, GraphProperties properties) {
        this.suggestionRepository = suggestionRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public int rebuildFor(Article article, List<Article> candidates) {
        suggestionRepository.deleteInvolving(article.getId());

        Set<String> articleTerms = termsOf(article);
        if (articleTerms.isEmpty()) {
            return 0;
        }

        List<SuggestedRelationship> suggestions = new ArrayList<>();
        for (Article candidate : candidates) {
            if (candidate.getId().equals(article.getId())) {
                continue;
            }
            Set<String> candidateTerms = termsOf(candidate);
            double score = jaccard(articleTerms, candidateTerms);
            if (score < MINIMUM_SCORE) {
                continue;
            }
            suggestions.add(new SuggestedRelationship(
                    article.getId(), candidate.getId(), kindOf(article, candidate), score, GENERATOR));
        }

        List<SuggestedRelationship> kept = suggestions.stream()
                .sorted((left, right) -> Double.compare(right.getScore(), left.getScore()))
                .limit(properties.maxSuggestionsPerArticle())
                .toList();
        suggestionRepository.saveAll(kept);
        return kept.size();
    }

    @Override
    @Transactional
    public void removeFor(UUID articleId) {
        suggestionRepository.deleteInvolving(articleId);
    }

    /**
     * Topics are the stronger signal, so a topic overlap is reported as such even when tags also
     * overlap. A reader told "related by topic" learns more than one told "related by tag".
     */
    private SuggestedRelationship.RelationshipKind kindOf(Article left, Article right) {
        Set<String> leftTopics = left.getTopics().stream()
                .map(topic -> topic.getSlug()).collect(Collectors.toSet());
        boolean sharesTopic = right.getTopics().stream()
                .anyMatch(topic -> leftTopics.contains(topic.getSlug()));
        return sharesTopic
                ? SuggestedRelationship.RelationshipKind.SHARED_TOPIC
                : SuggestedRelationship.RelationshipKind.SHARED_TAG;
    }

    private Set<String> termsOf(Article article) {
        Set<String> terms = new LinkedHashSet<>();
        article.getTopics().forEach(topic -> terms.add("topic:" + topic.getSlug()));
        article.getTags().forEach(tag -> terms.add("tag:" + tag.getSlug()));
        return terms;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }
}

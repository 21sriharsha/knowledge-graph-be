package com.knowledge.platform.graph.service;

import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.graph.model.dto.GraphEdge;
import com.knowledge.platform.graph.model.dto.GraphNeighbourhood;
import com.knowledge.platform.graph.model.dto.GraphNode;
import com.knowledge.platform.graph.model.entity.ArticleLink;
import com.knowledge.platform.graph.model.entity.SuggestedRelationship;
import com.knowledge.platform.graph.repository.ArticleLinkRepository;
import com.knowledge.platform.graph.repository.SuggestedRelationshipRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default GraphService.
 *
 * <p>See {@link GraphService} for what this provides and why it exists.
 */
@Service
@Transactional(readOnly = true)
public class DefaultGraphServiceImpl implements GraphService {

    private final ArticleLinkRepository linkRepository;
    private final SuggestedRelationshipRepository suggestionRepository;
    private final ArticleService articleService;
    private final GraphProperties properties;

    public DefaultGraphServiceImpl(
            ArticleLinkRepository linkRepository,
            SuggestedRelationshipRepository suggestionRepository,
            ArticleService articleService,
            GraphProperties properties) {
        this.linkRepository = linkRepository;
        this.suggestionRepository = suggestionRepository;
        this.articleService = articleService;
        this.properties = properties;
    }

    @Override
    @Cacheable(cacheNames = "graphNeighbourhoods", key = "#slug.value() + ':' + #requestedDepth + ':' + #includeSuggestions")
    public GraphNeighbourhood neighbourhoodOf(Slug slug, int requestedDepth, boolean includeSuggestions) {
        Article origin = articleService.findBySlug(slug)
                .orElseThrow(() -> NotFoundException.of("Article", slug.value()));
        return traverse(origin, requestedDepth, includeSuggestions);
    }

    @Override
    public GraphNeighbourhood neighbourhoodOf(UUID articleId, int requestedDepth, boolean includeSuggestions) {
        Article origin = articleService.findById(articleId)
                .orElseThrow(() -> NotFoundException.of("Article", articleId));
        return traverse(origin, requestedDepth, includeSuggestions);
    }

    @Override
    @Cacheable(cacheNames = "graphNeighbourhoods", key = "'overview:' + #maxNodes")
    public GraphNeighbourhood overview(int maxNodes) {
        int cap = Math.clamp(maxNodes, 1, properties.maxNodes());

        // Seed from the most-connected articles rather than the newest. A sample of recent articles
        // is frequently a set of disconnected dots, which makes the graph look broken; seeding from
        // the hubs shows the corpus where it is genuinely joined up.
        List<UUID> seeds = linkRepository.findMostLinkedArticleIds(PageRequest.of(0, cap));

        Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (UUID seed : seeds) {
            if (nodes.size() >= cap) {
                break;
            }
            articleService.findById(seed)
                    .filter(article -> article.getPublicationState().isPublic())
                    .ifPresent(article -> nodes.put(article.getId(), toNode(article, 0)));
        }

        // Fill any remaining budget with recently published articles, so a corpus with few links
        // still produces a picture instead of a nearly empty canvas.
        if (nodes.size() < cap) {
            articleService.findPublished(PageRequest.of(0, cap - nodes.size())).getContent()
                    .forEach(article -> nodes.putIfAbsent(article.getId(), toNode(article, 0)));
        }

        // Only edges between articles that made the cut; anything else would dangle.
        for (ArticleLink link : linkRepository.findBySourceArticleIdIn(nodes.keySet())) {
            if (link.isResolved() && nodes.containsKey(link.getTargetArticleId())) {
                edges.add(GraphEdge.authored(
                        link.getSourceArticleId(), link.getTargetArticleId(), link.getDisplayText()));
            }
        }

        boolean truncated = seeds.size() >= cap;
        return new GraphNeighbourhood(List.copyOf(nodes.values()), edges.stream().distinct().toList(),
                1, truncated);
    }

    @Override
    public List<UUID> mostConnectedArticleIds(int limit) {
        return linkRepository.findMostLinkedArticleIds(
                PageRequest.of(0, Math.clamp(limit, 1, properties.maxNodes())));
    }

    private GraphNeighbourhood traverse(Article origin, int requestedDepth, boolean includeSuggestions) {
        int depth = Math.clamp(requestedDepth, 1, properties.maxDepth());

        // LinkedHashMap so the origin stays first and the result is deterministic for a given corpus,
        // which matters for cache keys being meaningful and for tests being stable.
        Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Map<UUID, Article> articleCache = new HashMap<>();

        articleCache.put(origin.getId(), origin);
        nodes.put(origin.getId(), toNode(origin, 0));

        Deque<Frontier> queue = new ArrayDeque<>();
        queue.add(new Frontier(origin.getId(), 0));
        visited.add(origin.getId());
        boolean truncated = false;

        while (!queue.isEmpty()) {
            Frontier current = queue.poll();
            if (current.distance() >= depth) {
                continue;
            }

            List<UUID> neighbours = new ArrayList<>();

            for (ArticleLink link : linkRepository.findBySourceArticleId(current.articleId())) {
                if (!link.isResolved()) {
                    // Unresolved edges are real -- the author wrote them -- but they have no node to
                    // point at, so a graph view cannot draw them. They surface as ingestion
                    // diagnostics and in the article read model instead.
                    continue;
                }
                edges.add(GraphEdge.authored(
                        current.articleId(), link.getTargetArticleId(), link.getDisplayText()));
                neighbours.add(link.getTargetArticleId());
            }

            for (ArticleLink backlink : linkRepository.findByTargetArticleId(current.articleId())) {
                edges.add(GraphEdge.authored(
                        backlink.getSourceArticleId(), current.articleId(), backlink.getDisplayText()));
                neighbours.add(backlink.getSourceArticleId());
            }

            if (includeSuggestions) {
                for (SuggestedRelationship suggestion : suggestionRepository
                        .findBySourceArticleIdOrderByScoreDesc(current.articleId(),
                                PageRequest.of(0, properties.maxSuggestionsPerArticle()))) {
                    edges.add(GraphEdge.suggested(
                            current.articleId(), suggestion.getTargetArticleId(),
                            suggestion.getRelationshipKind().name(), suggestion.getScore()));
                    neighbours.add(suggestion.getTargetArticleId());
                }
            }

            for (UUID neighbourId : neighbours) {
                if (!visited.add(neighbourId)) {
                    continue;
                }
                if (nodes.size() >= properties.maxNodes()) {
                    truncated = true;
                    break;
                }
                Article neighbour = articleCache.computeIfAbsent(
                        neighbourId, id -> articleService.findById(id).orElse(null));
                if (neighbour == null) {
                    continue;
                }
                nodes.put(neighbourId, toNode(neighbour, current.distance() + 1));
                queue.add(new Frontier(neighbourId, current.distance() + 1));
            }

            if (truncated) {
                break;
            }
        }

        // Edges to nodes that were cut by the node cap would dangle; a client cannot draw them.
        List<GraphEdge> connected = edges.stream()
                .filter(edge -> nodes.containsKey(edge.sourceId()) && nodes.containsKey(edge.targetId()))
                .distinct()
                .toList();

        return new GraphNeighbourhood(List.copyOf(nodes.values()), connected, depth, truncated);
    }

    private GraphNode toNode(Article article, int distance) {
        // Read inside the transaction: primaryTopic is a lazy association, and resolving it after
        // the service method returns would throw.
        String topic = article.getPrimaryTopic() == null ? null : article.getPrimaryTopic().getName();
        return new GraphNode(article.getId(), article.getSlug(), article.getTitle(), distance, topic);
    }

    /** One step of the breadth-first walk. */
    private record Frontier(UUID articleId, int distance) {
    }
}

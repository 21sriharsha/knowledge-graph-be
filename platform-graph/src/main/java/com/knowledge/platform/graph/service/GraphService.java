package com.knowledge.platform.graph.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.graph.model.dto.GraphNeighbourhood;
import java.util.List;
import java.util.UUID;

/**
 * Bounded traversal of the knowledge graph.
 *
 * <p>Every query returns a neighbourhood, never the graph. The traversal is a breadth-first walk with
 * three independent limits -- requested depth, configured maximum depth, and a hard node cap -- and
 * it stops at whichever binds first. A knowledge graph is densely connected: a hub article at depth
 * three can reach most of the corpus, which is both an expensive query and a picture nobody can read.
 */
public interface GraphService {

    /**
     * The neighbourhood around one article.
     *
     * <p>Cached on (slug, depth, includeSuggestions). Traversal cost grows with connectivity, and the
     * same hub article is requested repeatedly from the read path; ingestion invalidates this cache
     * when the article's edges change.
     */
    GraphNeighbourhood neighbourhoodOf(Slug slug, int requestedDepth, boolean includeSuggestions);

    GraphNeighbourhood neighbourhoodOf(UUID articleId, int requestedDepth, boolean includeSuggestions);

    /**
     * A bounded sample of the whole graph, for an overview surface such as a landing page.
     *
     * <p>Deliberately a <em>sample</em>, not the graph. Returning everything would be an unbounded
     * query on an anonymous endpoint, which is a denial-of-service primitive, and no client can draw
     * a corpus-sized graph usefully anyway. The sample is seeded from the most-connected articles,
     * so the picture shows the parts of the corpus that are actually joined up rather than an
     * arbitrary slice.
     */
    GraphNeighbourhood overview(int maxNodes);

    /** The most-linked articles, most-linked first. */
    List<UUID> mostConnectedArticleIds(int limit);
}

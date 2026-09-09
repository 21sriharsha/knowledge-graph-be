package com.knowledge.platform.graph.delegate;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.graph.model.dto.GraphNeighbourhood;
import com.knowledge.platform.graph.service.GraphService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates between the graph HTTP contract and the graph domain. */
@Component
public class GraphDelegate {

    private final GraphService graphService;

    public GraphDelegate(GraphService graphService) {
        this.graphService = graphService;
    }

    public GraphNeighbourhood neighbourhoodOf(String slug, int depth, boolean includeSuggestions) {
        return graphService.neighbourhoodOf(Slug.of(slug), depth, includeSuggestions);
    }

    public GraphNeighbourhood neighbourhoodOf(UUID nodeId, int depth, boolean includeSuggestions) {
        return graphService.neighbourhoodOf(nodeId, depth, includeSuggestions);
    }

    public GraphNeighbourhood overview(int maxNodes) {
        return graphService.overview(maxNodes);
    }
}

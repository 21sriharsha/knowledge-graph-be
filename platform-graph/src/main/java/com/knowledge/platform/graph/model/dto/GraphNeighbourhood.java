package com.knowledge.platform.graph.model.dto;

import java.util.List;

/**
 * A bounded slice of the knowledge graph.
 *
 * <p>Bounded is the operative word: the graph API returns a neighbourhood, never the whole graph.
 * {@code truncated} says a limit was reached, so a client can offer "expand" rather than silently
 * presenting a partial picture as if it were complete.
 */
public record GraphNeighbourhood(
        List<GraphNode> nodes, List<GraphEdge> edges, int depth, boolean truncated) {

    public GraphNeighbourhood {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public static GraphNeighbourhood empty() {
        return new GraphNeighbourhood(List.of(), List.of(), 0, false);
    }
}

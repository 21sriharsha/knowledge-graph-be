package com.knowledge.platform.graph.controller;

import com.knowledge.platform.graph.delegate.GraphDelegate;
import com.knowledge.platform.graph.model.dto.GraphNeighbourhood;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public knowledge-graph exploration.
 *
 * <p>Every response is a bounded neighbourhood. There is deliberately no endpoint that returns the
 * whole graph: it would be an unbounded query on a public endpoint, and no client can usefully render
 * one anyway.
 */
@RestController
@RequestMapping("/api/graph")
@Validated
@Tag(name = "Graph", description = "Bounded neighbourhoods of the knowledge graph")
public class GraphController {

    private final GraphDelegate graphDelegate;

    public GraphController(GraphDelegate graphDelegate) {
        this.graphDelegate = graphDelegate;
    }

    @GetMapping
    @Operation(summary = "Neighbourhood around an article, addressed by slug")
    public GraphNeighbourhood bySlug(
            @RequestParam String slug,
            @Parameter(description = "Hops from the origin; clamped to knowledge.graph.max-depth")
            @RequestParam(defaultValue = "1") @Min(1) @Max(5) int depth,
            @RequestParam(defaultValue = "false") boolean includeSuggestions) {
        return graphDelegate.neighbourhoodOf(slug, depth, includeSuggestions);
    }

    @GetMapping("/overview")
    @Operation(summary = "A bounded sample of the whole graph, for an overview surface",
            description = """
                    Returns a capped sample seeded from the most-connected articles, never the whole
                    graph: an unbounded traversal on an anonymous endpoint is a denial-of-service
                    primitive, and no client can usefully draw a corpus-sized graph anyway.
                    """)
    public GraphNeighbourhood overview(
            @RequestParam(defaultValue = "40") @Min(1) @Max(150) int maxNodes) {
        return graphDelegate.overview(maxNodes);
    }

    @GetMapping("/{nodeId}")
    @Operation(summary = "Neighbourhood around an article, addressed by id")
    public GraphNeighbourhood byNodeId(
            @PathVariable UUID nodeId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(5) int depth,
            @RequestParam(defaultValue = "false") boolean includeSuggestions) {
        return graphDelegate.neighbourhoodOf(nodeId, depth, includeSuggestions);
    }
}

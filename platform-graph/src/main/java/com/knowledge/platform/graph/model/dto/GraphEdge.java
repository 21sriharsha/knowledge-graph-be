package com.knowledge.platform.graph.model.dto;

import java.util.UUID;

/**
 * An edge in a returned neighbourhood.
 *
 * <p>{@code authored} separates what a person wrote from what the platform inferred. A reader looking
 * at a graph deserves to know which connections were deliberate, and a client cannot recover that
 * distinction if both arrive as the same kind of line.
 */
public record GraphEdge(
        UUID sourceId, UUID targetId, String label, boolean authored, String kind, double weight) {

    public static GraphEdge authored(UUID sourceId, UUID targetId, String label) {
        return new GraphEdge(sourceId, targetId, label, true, "LINK", 1.0);
    }

    public static GraphEdge suggested(UUID sourceId, UUID targetId, String kind, double score) {
        return new GraphEdge(sourceId, targetId, null, false, kind, score);
    }
}

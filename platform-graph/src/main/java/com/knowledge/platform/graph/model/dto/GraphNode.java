package com.knowledge.platform.graph.model.dto;

import java.util.UUID;

/**
 * A node in a returned neighbourhood.
 *
 * @param distance hops from the node the neighbourhood was built around, so a client can lay the
 *     graph out without recomputing traversal depth
 * @param topic the article's primary topic, or null. Carried so a client can colour and group nodes
 *     by subject. Without it the only thing a colour could encode is distance, which a reader
 *     already sees from the layout — the subject is the part that makes a graph legible at a glance.
 */
public record GraphNode(UUID id, String slug, String title, int distance, String topic) {
}

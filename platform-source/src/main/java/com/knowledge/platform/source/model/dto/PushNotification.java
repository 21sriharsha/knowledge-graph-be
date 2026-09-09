package com.knowledge.platform.source.model.dto;

import java.util.List;

/**
 * A push event, normalized from a provider webhook.
 *
 * <p>Carrying the changed paths rather than only the revision is what lets ingestion process one
 * file instead of walking an entire repository for a one-line edit.
 *
 * @param deliveryId the provider's delivery identifier, used to make retried deliveries idempotent
 * @param branch the branch pushed to; pushes to other branches are ignored
 * @param revision the head revision after the push
 * @param changedPaths paths added or modified
 * @param removedPaths paths deleted
 */
public record PushNotification(
        String deliveryId,
        String branch,
        String revision,
        List<String> changedPaths,
        List<String> removedPaths) {

    public PushNotification {
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
        removedPaths = removedPaths == null ? List.of() : List.copyOf(removedPaths);
    }

    /**
     * Whether the provider told us which files changed.
     *
     * <p>Some providers omit the file list on large pushes. When that happens ingestion has to fall
     * back to a full repository walk, so the distinction has to survive normalization rather than
     * being flattened into an empty list that looks like "nothing changed".
     */
    public boolean hasPathDetail() {
        return !changedPaths.isEmpty() || !removedPaths.isEmpty();
    }
}

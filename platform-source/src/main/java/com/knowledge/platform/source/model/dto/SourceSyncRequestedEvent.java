package com.knowledge.platform.source.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Published when a repository's content should be materialized.
 *
 * <p>An application event rather than a direct call, because ingestion depends on source and the
 * reverse dependency would be a cycle -- one the Maven reactor would reject outright. The source
 * module knows that something needs ingesting; it does not know what ingestion is.
 *
 * @param trigger what caused this, recorded on the ingestion run
 * @param changedPaths paths to process, empty when the whole repository should be walked
 * @param removedPaths paths whose articles should be withdrawn
 * @param fullResync true when the provider gave no path detail, or a caller asked for everything
 */
public record SourceSyncRequestedEvent(
        UUID repositoryId,
        String revision,
        Trigger trigger,
        List<String> changedPaths,
        List<String> removedPaths,
        boolean fullResync) {

    public SourceSyncRequestedEvent {
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
        removedPaths = removedPaths == null ? List.of() : List.copyOf(removedPaths);
    }

    /** What caused a sync. Mirrors the {@code trigger_type} values on {@code ingestion.runs}. */
    public enum Trigger {
        WEBHOOK,
        MANUAL,
        SCHEDULED
    }

    public static SourceSyncRequestedEvent fullResync(
            UUID repositoryId, String revision, Trigger trigger) {
        return new SourceSyncRequestedEvent(repositoryId, revision, trigger, List.of(), List.of(), true);
    }
}

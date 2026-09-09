package com.knowledge.platform.graph.model.entity;

/** Whether an author's reference currently points at an article that exists. */
public enum LinkResolutionState {
    /** The target slug matches a stored article. */
    RESOLVED,
    /**
     * No article has that slug yet.
     *
     * <p>Kept rather than discarded. The reference is what the author wrote, and it becomes valid the
     * moment the target is published -- which is how a wiki is supposed to behave. Discarding it
     * would also lose the ingestion diagnostic that tells the author about the dangling reference.
     */
    UNRESOLVED
}

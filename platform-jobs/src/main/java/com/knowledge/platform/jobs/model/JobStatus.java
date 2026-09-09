package com.knowledge.platform.jobs.model;

import java.time.Instant;

/** A point-in-time observation of a job, for diagnostics and the studio API. */
public record JobStatus(
        String idempotencyKey,
        String type,
        JobState state,
        int attempts,
        Instant submittedAt,
        Instant updatedAt,
        String failureMessage) {

    public boolean isTerminal() {
        return state == JobState.SUCCEEDED || state == JobState.FAILED || state == JobState.DEDUPLICATED;
    }
}

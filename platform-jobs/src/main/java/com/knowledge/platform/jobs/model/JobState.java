package com.knowledge.platform.jobs.model;

/** Observable lifecycle of a submitted job. */
public enum JobState {
    /** Accepted by the queue, not yet started. */
    PENDING,
    /** Currently executing. */
    RUNNING,
    /** A prior attempt failed and another is scheduled. */
    RETRYING,
    /** Completed without error. */
    SUCCEEDED,
    /** Exhausted its retry policy, or was refused by a saturated executor. */
    FAILED,
    /** Collapsed into an identical job that was already in flight. */
    DEDUPLICATED
}

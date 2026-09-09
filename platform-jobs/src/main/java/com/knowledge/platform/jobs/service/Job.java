package com.knowledge.platform.jobs.service;

import com.knowledge.platform.jobs.model.RetryPolicy;

/**
 * A unit of deferred work.
 *
 * <p>Jobs are what keep expensive materialization -- embeddings, search indexing, repository walks --
 * off the request thread. The contract is deliberately narrow so that the current in-process queue
 * can later be replaced by a durable one without touching a single caller.
 *
 * <p>Implementations must be <strong>idempotent</strong>. The queue may execute a job more than once
 * today (retries) and will do so under at-least-once semantics if a durable queue replaces it.
 */
public interface Job {

    /**
     * A stable identity derived from what the job operates on, never from when it was created.
     *
     * <p>Two submissions describing the same work must produce the same key, so the queue can collapse
     * duplicates. A webhook delivered twice must not enqueue the same embedding twice.
     */
    String idempotencyKey();

    /** Coarse job category, used as a metric tag and in diagnostics. */
    String type();

    /** Performs the work. Throwing signals failure and makes the job eligible for retry. */
    void execute();

    /** Retry behaviour for this job. Defaults to a single attempt. */
    default RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}

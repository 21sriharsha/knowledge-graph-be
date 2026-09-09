package com.knowledge.platform.jobs.service;

import com.knowledge.platform.jobs.model.JobStatus;
import java.util.List;
import java.util.Optional;

/**
 * The application's boundary for deferred work.
 *
 * <p>Callers depend on this interface and nothing else in the module. The v1 implementation is
 * in-process and bounded; a database-backed or distributed implementation can replace it later
 * without any caller changing, which is the entire reason the abstraction exists this early.
 */
public interface JobQueue {

    /**
     * Accepts a job for execution.
     *
     * <p>If a job with the same {@link Job#idempotencyKey()} is already in flight, the submission is
     * collapsed into it and a {@link com.knowledge.platform.jobs.model.JobState#DEDUPLICATED} status
     * is returned rather than a second execution being scheduled.
     */
    JobStatus submit(Job job);

    /** Current or most recent status of a job, while the queue still remembers it. */
    Optional<JobStatus> statusOf(String idempotencyKey);

    /** Recently observed statuses, newest first. Bounded; for diagnostics only. */
    List<JobStatus> recentStatuses(int limit);
}

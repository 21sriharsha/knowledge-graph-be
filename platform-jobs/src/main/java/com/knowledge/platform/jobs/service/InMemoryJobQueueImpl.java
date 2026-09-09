package com.knowledge.platform.jobs.service;

import com.knowledge.platform.jobs.model.JobState;
import com.knowledge.platform.jobs.model.JobStatus;
import com.knowledge.platform.jobs.model.RetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * In-process {@link JobQueue} backed by a bounded {@link AsyncTaskExecutor}.
 *
 * <p>Deliberately not durable: jobs are lost on shutdown. That is an accepted v1 trade-off, because
 * every job in this system re-derives state that is rebuildable from canonical content, and because
 * an ingestion run can simply be replayed.
 *
 * <p>What this class does implement is the behaviour a durable queue would also have to implement --
 * deduplication of in-flight work, bounded retries with backoff, and observable status. Building
 * those here rather than assuming them keeps the eventual swap a local change instead of a
 * behavioural surprise.
 */
@Slf4j
@Service
public class InMemoryJobQueueImpl implements JobQueue {

    /** How many terminal statuses to retain for diagnostics before evicting the oldest. */
    private static final int STATUS_HISTORY_LIMIT = 500;

    private final AsyncTaskExecutor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final MeterRegistry meterRegistry;

    /** Accepted and not yet terminal, keyed by idempotency key. Also the deduplication set. */
    private final Map<String, JobStatus> inFlight = new ConcurrentHashMap<>();

    /** Bounded, insertion-ordered history of terminal statuses. */
    private final Map<String, JobStatus> history = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, JobStatus> eldest) {
                    return size() > STATUS_HISTORY_LIMIT;
                }
            });

    public InMemoryJobQueueImpl(
            AsyncTaskExecutor jobTaskExecutor,
            TaskScheduler jobTaskScheduler,
            MeterRegistry meterRegistry) {
        this.taskExecutor = jobTaskExecutor;
        this.taskScheduler = jobTaskScheduler;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public JobStatus submit(Job job) {
        Instant now = Instant.now();
        JobStatus pending =
                new JobStatus(job.idempotencyKey(), job.type(), JobState.PENDING, 0, now, now, null);

        JobStatus existing = inFlight.putIfAbsent(job.idempotencyKey(), pending);
        if (existing != null) {
            meterRegistry.counter("knowledge.jobs.deduplicated", "type", job.type()).increment();
            log.debug("Job {} [{}] collapsed into an in-flight submission",
                    job.idempotencyKey(), job.type());
            return new JobStatus(job.idempotencyKey(), job.type(), JobState.DEDUPLICATED,
                    existing.attempts(), existing.submittedAt(), now, null);
        }

        meterRegistry.counter("knowledge.jobs.submitted", "type", job.type()).increment();
        dispatch(job, 1, pending.submittedAt());
        return pending;
    }

    @Override
    public Optional<JobStatus> statusOf(String idempotencyKey) {
        JobStatus current = inFlight.get(idempotencyKey);
        return Optional.ofNullable(current != null ? current : history.get(idempotencyKey));
    }

    @Override
    public List<JobStatus> recentStatuses(int limit) {
        List<JobStatus> all = new ArrayList<>(inFlight.values());
        synchronized (history) {
            all.addAll(history.values());
        }
        return all.stream()
                .sorted(Comparator.comparing(JobStatus::updatedAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    private void dispatch(Job job, int attempt, Instant submittedAt) {
        try {
            taskExecutor.execute(() -> run(job, attempt, submittedAt));
        } catch (RejectedExecutionException e) {
            // The bounded queue is full. Recording this as a terminal failure is deliberate: silently
            // dropping derived work would leave the system permanently inconsistent with canonical
            // content and no trace of why. A visible failure is recoverable by re-running ingestion.
            complete(job, attempt, submittedAt, JobState.FAILED,
                    "rejected by executor: " + e.getMessage());
            meterRegistry.counter("knowledge.jobs.rejected", "type", job.type()).increment();
            log.error("Job {} [{}] rejected; the job executor is saturated",
                    job.idempotencyKey(), job.type(), e);
        }
    }

    private void run(Job job, int attempt, Instant submittedAt) {
        markRunning(job, attempt, submittedAt);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            job.execute();
            sample.stop(meterRegistry.timer(
                    "knowledge.jobs.duration", "type", job.type(), "outcome", "success"));
            complete(job, attempt, submittedAt, JobState.SUCCEEDED, null);
            meterRegistry.counter("knowledge.jobs.succeeded", "type", job.type()).increment();
        } catch (Exception e) {
            sample.stop(meterRegistry.timer(
                    "knowledge.jobs.duration", "type", job.type(), "outcome", "failure"));
            handleFailure(job, attempt, submittedAt, e);
        }
    }

    private void handleFailure(Job job, int attempt, Instant submittedAt, Exception failure) {
        RetryPolicy policy = job.retryPolicy();
        if (attempt >= policy.maxAttempts()) {
            complete(job, attempt, submittedAt, JobState.FAILED, failure.toString());
            meterRegistry.counter("knowledge.jobs.failed", "type", job.type()).increment();
            log.error("Job {} [{}] failed permanently after {} attempt(s)",
                    job.idempotencyKey(), job.type(), attempt, failure);
            return;
        }

        int nextAttempt = attempt + 1;
        Duration backoff = policy.backoffBeforeAttempt(nextAttempt);
        inFlight.computeIfPresent(job.idempotencyKey(), (key, current) -> new JobStatus(
                current.idempotencyKey(), current.type(), JobState.RETRYING, attempt,
                current.submittedAt(), Instant.now(), failure.toString()));
        meterRegistry.counter("knowledge.jobs.retried", "type", job.type()).increment();
        log.warn("Job {} [{}] failed on attempt {}; retrying in {}",
                job.idempotencyKey(), job.type(), attempt, backoff, failure);
        taskScheduler.schedule(
                () -> dispatch(job, nextAttempt, submittedAt), Instant.now().plus(backoff));
    }

    private void markRunning(Job job, int attempt, Instant submittedAt) {
        inFlight.put(job.idempotencyKey(), new JobStatus(job.idempotencyKey(), job.type(),
                JobState.RUNNING, attempt, submittedAt, Instant.now(), null));
    }

    private void complete(
            Job job, int attempt, Instant submittedAt, JobState state, String failureMessage) {
        JobStatus terminal = new JobStatus(job.idempotencyKey(), job.type(), state, attempt,
                submittedAt, Instant.now(), failureMessage);
        // Remove from in-flight last: while the key is present, a concurrent submit deduplicates
        // against it rather than starting a second execution of work that is still finishing.
        history.put(job.idempotencyKey(), terminal);
        inFlight.remove(job.idempotencyKey());
    }
}

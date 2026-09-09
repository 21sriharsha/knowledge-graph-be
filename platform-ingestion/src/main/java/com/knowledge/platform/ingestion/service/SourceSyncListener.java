package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.jobs.model.RetryPolicy;
import com.knowledge.platform.jobs.service.Job;
import com.knowledge.platform.jobs.service.JobQueue;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a sync request into a background job.
 *
 * <p>Listens on {@code AFTER_COMMIT}, which matters twice over: a webhook whose delivery-ledger insert
 * rolls back must not trigger ingestion, and a repository connected in a transaction that then fails
 * must not be synced. Handling the event inline would let ingestion see rows that never existed.
 *
 * <p>The work is queued rather than run here, because the publisher is usually an HTTP thread --
 * a webhook the provider expects to acknowledge in milliseconds, or a studio request. A repository
 * walk on that thread would mean the provider timing out and retrying the delivery it just sent.
 */
@Slf4j
@Component
public class SourceSyncListener {

    private static final String JOB_TYPE = "repository-ingestion";

    private final IngestionService ingestionService;
    private final JobQueue jobQueue;

    public SourceSyncListener(IngestionService ingestionService, JobQueue jobQueue) {
        this.ingestionService = ingestionService;
        this.jobQueue = jobQueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSyncRequested(SourceSyncRequestedEvent event) {
        log.debug("Queueing ingestion for repository {} at revision {}",
                event.repositoryId(), event.revision());
        jobQueue.submit(new RepositoryIngestionJob(event));
    }

    /**
     * Deferred repository ingestion.
     *
     * <p>Idempotent by construction: the key is the repository plus the revision, so two webhooks for
     * the same commit collapse into one run, and re-running is safe because every document is
     * hash-compared before anything derived is rewritten.
     */
    private final class RepositoryIngestionJob implements Job {

        private final SourceSyncRequestedEvent event;

        private RepositoryIngestionJob(SourceSyncRequestedEvent event) {
            this.event = event;
        }

        @Override
        public String idempotencyKey() {
            return JOB_TYPE + ":" + event.repositoryId() + ":" + event.revision();
        }

        @Override
        public String type() {
            return JOB_TYPE;
        }

        @Override
        public void execute() {
            ingestionService.ingest(event);
        }

        @Override
        public RetryPolicy retryPolicy() {
            // Retried because the common failure is a transient provider outage, and re-running is
            // safe. The backoff is generous: a provider that is down or rate-limiting is not helped
            // by being asked again immediately.
            return RetryPolicy.exponential(3, Duration.ofSeconds(30));
        }
    }
}

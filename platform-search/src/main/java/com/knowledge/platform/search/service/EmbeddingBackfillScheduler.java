package com.knowledge.platform.search.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link EmbeddingBackfillService} on a fixed schedule.
 *
 * <p>Separate from the service for the same reason as source reconciliation: the logic stays callable
 * without a scheduler, and switching it off is configuration rather than code.
 *
 * <p><b>TODO (before running more than one replica): guard with ShedLock.</b> {@code @Scheduled}
 * fires on every running instance, so with several replicas they all run this. That is currently
 * harmless -- the work is only <em>requested</em> here, and the job queue deduplicates it while the
 * pipeline is idempotent -- but it is wasted inference calls, and it gets worse with every
 * replica added.
 *
 * <p>The fix is {@code shedlock-spring} plus {@code shedlock-provider-jdbc-template}, annotating
 * this method with {@code @SchedulerLock}: the lock lives in a PostgreSQL table the platform already
 * has, so it needs no new infrastructure, and it locks per task rather than electing a leader per
 * instance. See BACKEND-SPEC section 4.9, "Scheduled tasks".
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge.search.embedding-backfill", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EmbeddingBackfillScheduler {

    private final EmbeddingBackfillService backfillService;

    public EmbeddingBackfillScheduler(EmbeddingBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Scheduled(
            initialDelayString = "${knowledge.search.embedding-backfill.initial-delay:PT3M}",
            fixedDelayString = "${knowledge.search.embedding-backfill.interval:PT10M}")
    public void backfill() {
        backfillService.backfill();
    }
}

package com.knowledge.platform.source.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link SourceReconciliationService} on a fixed schedule.
 *
 * <p>Separated from the service so the reconciliation logic can be invoked directly -- from a test,
 * or from a studio endpoint -- without a scheduler running, and so that switching it off is a
 * configuration change rather than a code change.
 *
 * <p>The default interval is deliberately slow. This is a safety net behind webhooks, not the primary
 * path: a short interval would spend a provider's rate limit on the overwhelmingly common answer that
 * nothing has changed.
 *
 * <p><b>TODO (before running more than one replica): guard with ShedLock.</b> {@code @Scheduled}
 * fires on every running instance, so with several replicas they all run this. That is currently
 * harmless -- the work is only <em>requested</em> here, and the job queue deduplicates it while the
 * pipeline is idempotent -- but it is wasted provider API calls, and it gets worse with every
 * replica added.
 *
 * <p>The fix is {@code shedlock-spring} plus {@code shedlock-provider-jdbc-template}, annotating
 * this method with {@code @SchedulerLock}: the lock lives in a PostgreSQL table the platform already
 * has, so it needs no new infrastructure, and it locks per task rather than electing a leader per
 * instance. See BACKEND-SPEC section 4.9, "Scheduled tasks".
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge.source.reconciliation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SourceReconciliationScheduler {

    private final SourceReconciliationService reconciliationService;

    public SourceReconciliationScheduler(SourceReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * {@code fixedDelay} rather than {@code fixedRate}: the delay is measured from the end of the
     * previous run, so a slow provider cannot cause overlapping reconciliations to pile up.
     */
    @Scheduled(
            initialDelayString = "${knowledge.source.reconciliation.initial-delay:PT2M}",
            fixedDelayString = "${knowledge.source.reconciliation.interval:PT15M}")
    public void reconcile() {
        log.debug("Starting scheduled source reconciliation");
        reconciliationService.reconcile();
    }
}

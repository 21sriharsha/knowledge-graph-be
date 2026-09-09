package com.knowledge.platform.source.service;

import com.knowledge.platform.source.integration.adapter.SourceAdapterException;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default {@link SourceReconciliationService}.
 *
 * <p>Deliberately cheap. Reconciliation asks each provider for one branch head and compares it to a
 * stored string; it does not walk trees or fetch content. Only a repository whose head has actually
 * moved gets a sync request, and that request goes through the same idempotent pipeline as a webhook
 * -- so a reconciliation that races a webhook costs a deduplicated job, not duplicate content.
 */
@Slf4j
@Service
public class DefaultSourceReconciliationServiceImpl implements SourceReconciliationService {

    private final SourceService sourceService;

    public DefaultSourceReconciliationServiceImpl(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public int reconcile() {
        List<SourceRepository> repositories = sourceService.findActive();
        if (repositories.isEmpty()) {
            return 0;
        }

        int behind = 0;
        for (SourceRepository repository : repositories) {
            try {
                if (reconcileOne(repository)) {
                    behind++;
                }
            } catch (SourceAdapterException e) {
                // One unreachable provider must not stop the others from being reconciled. There is
                // nothing to repair here and nothing is corrupted -- the next pass will retry.
                log.warn("Could not reach {} during reconciliation: {}", repository, e.getMessage());
            } catch (RuntimeException e) {
                log.error("Reconciliation failed unexpectedly for {}", repository, e);
            }
        }

        if (behind > 0) {
            log.info("Reconciliation found {} of {} repositories behind; syncs requested",
                    behind, repositories.size());
        }
        return behind;
    }

    private boolean reconcileOne(SourceRepository repository) {
        Optional<String> head = sourceService.resolveCurrentRevision(repository);
        if (head.isEmpty()) {
            log.debug("No head revision available for {}; skipping", repository);
            return false;
        }

        String current = head.get();
        if (current.equals(repository.getLastSyncedRevision())) {
            return false;
        }

        log.info("{} is at {} but last synced {}; requesting a sync",
                repository, current, repository.getLastSyncedRevision());
        sourceService.publishSync(SourceSyncRequestedEvent.fullResync(
                repository.getId(), current, SourceSyncRequestedEvent.Trigger.SCHEDULED));
        return true;
    }
}

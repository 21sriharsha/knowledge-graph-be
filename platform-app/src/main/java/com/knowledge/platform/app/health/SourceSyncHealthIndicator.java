package com.knowledge.platform.app.health;

import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.service.SourceService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether connected repositories are actually being synchronized.
 *
 * <p>This is the failure that is otherwise invisible. A repository whose webhook was never registered,
 * or whose deliveries stopped arriving, produces no errors at all -- it simply goes quiet, and the
 * content silently stops updating. Comparing each repository's last successful sync against the
 * reconciliation interval turns that silence into a signal.
 *
 * <p>Never DOWN, for the same reason as the AI indicator: stale content is a problem to investigate,
 * not a reason to pull a serving instance out of rotation.
 */
@Slf4j
@Component("sourceSync")
public class SourceSyncHealthIndicator implements HealthIndicator {

    private final SourceService sourceService;
    private final Duration staleAfter;

    public SourceSyncHealthIndicator(
            SourceService sourceService,
            @Value("${knowledge.source.reconciliation.stale-after:PT2H}") Duration staleAfter) {
        this.sourceService = sourceService;
        // Several reconciliation intervals, not one: a single missed pass is normal and should not
        // read as a fault.
        this.staleAfter = staleAfter;
    }

    @Override
    public Health health() {
        List<SourceRepository> active;
        try {
            active = sourceService.findActive();
        } catch (RuntimeException e) {
            log.debug("Could not read connected repositories for the health check", e);
            return Health.unknown().withDetail("error", "repositories unavailable").build();
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeRepositories", active.size());

        if (active.isEmpty()) {
            details.put("note", "No repositories are connected.");
            return Health.up().withDetails(details).build();
        }

        Instant threshold = Instant.now().minus(staleAfter);
        List<String> neverSynced = active.stream()
                .filter(repository -> repository.getLastSyncedAt() == null)
                .map(SourceRepository::getDisplayName)
                .toList();
        List<String> stale = active.stream()
                .filter(repository -> repository.getLastSyncedAt() != null
                        && repository.getLastSyncedAt().isBefore(threshold))
                .map(SourceRepository::getDisplayName)
                .toList();

        details.put("staleAfter", staleAfter.toString());
        if (!neverSynced.isEmpty()) {
            details.put("neverSynced", neverSynced);
        }
        if (!stale.isEmpty()) {
            details.put("stale", stale);
        }

        boolean healthy = neverSynced.isEmpty() && stale.isEmpty();
        if (!healthy) {
            details.put("impact", "Content from these repositories may be out of date. Check that "
                    + "their webhooks are registered and that reconciliation is enabled.");
        }
        return (healthy ? Health.up() : Health.status("DEGRADED")).withDetails(details).build();
    }
}

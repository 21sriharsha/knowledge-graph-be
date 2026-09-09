package com.knowledge.platform.app.health;

import com.knowledge.platform.ai.service.AiProperties;
import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.search.service.EmbeddingBackfillService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the AI subsystem without failing the application's health.
 *
 * <p>The status is deliberately never DOWN. An unavailable model degrades search relevance and delays
 * embeddings; it does not stop the platform serving content, and reporting DOWN would take a healthy
 * instance out of a load balancer over a subsystem the architecture explicitly treats as optional.
 *
 * <p>What it does report is the distinction operators actually need: whether AI is <em>switched off</em>
 * (a deliberate configuration) or <em>switched on but unreachable</em> (something to fix). The
 * embedding backlog is included because it is the visible consequence of the second case.
 */
@Slf4j
@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

    private final AiProperties properties;
    private final TextEmbeddingModel embeddingModel;
    private final EmbeddingBackfillService backfillService;

    public AiHealthIndicator(
            AiProperties properties,
            TextEmbeddingModel embeddingModel,
            EmbeddingBackfillService backfillService) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
        this.backfillService = backfillService;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", properties.enabled());

        if (!properties.enabled()) {
            details.put("mode", "deterministic");
            details.put("note", "Query understanding uses the heuristic analyzer; "
                    + "no embeddings are generated. This is a supported configuration.");
            return Health.up().withDetails(details).build();
        }

        boolean available = embeddingModel.isAvailable();
        details.put("embeddingProvider", available ? "available" : "unreachable");
        details.put("embeddingModel", embeddingModel.modelName());
        details.put("embeddingDimensions", embeddingModel.dimensions());

        if (available) {
            try {
                details.put("embeddingBacklog", backfillService.backlogSize());
            } catch (RuntimeException e) {
                // A health check must not throw; a failed backlog count is itself worth reporting.
                log.debug("Could not size the embedding backlog", e);
                details.put("embeddingBacklog", "unknown");
            }
        } else {
            details.put("impact", "Search runs lexically only; embeddings are backfilled on recovery.");
        }

        // Reported through the status name rather than UP/DOWN, so a monitor can alert on it while
        // the instance stays in rotation.
        return (available ? Health.up() : Health.status("DEGRADED")).withDetails(details).build();
    }
}

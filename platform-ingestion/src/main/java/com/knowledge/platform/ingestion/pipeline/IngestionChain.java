package com.knowledge.platform.ingestion.pipeline;

import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs the handler chain for one document.
 *
 * <p>The chain is assembled by Spring from every {@link IngestionHandler} bean, ordered by
 * {@code @Order}. Adding a step is declaring a bean; no existing handler and no list here changes.
 *
 * <p>A handler throwing is contained rather than propagated. One malformed document must not abort a
 * repository sync, so the failure is recorded as a diagnostic against that document and the run moves
 * on -- which is also what makes a retried run able to make progress on everything that did work.
 */
@Slf4j
@Component
public class IngestionChain {

    private final List<IngestionHandler> handlers;
    private final MeterRegistry meterRegistry;

    public IngestionChain(List<IngestionHandler> handlers, MeterRegistry meterRegistry) {
        // Spring injects the list already sorted by @Order.
        this.handlers = List.copyOf(handlers);
        this.meterRegistry = meterRegistry;
        log.info("Ingestion pipeline: {}", handlers.stream().map(IngestionHandler::name).toList());
    }

    public void execute(IngestionContext context) {
        for (IngestionHandler handler : handlers) {
            if (!handler.appliesTo(context)) {
                continue;
            }
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                handler.handle(context);
                sample.stop(meterRegistry.timer("knowledge.ingestion.handler",
                        "handler", handler.name(), "outcome", "success"));
            } catch (RuntimeException e) {
                sample.stop(meterRegistry.timer("knowledge.ingestion.handler",
                        "handler", handler.name(), "outcome", "failure"));
                log.error("Handler '{}' failed for {} [run {}]", handler.name(),
                        context.sourceFile().path(), context.run().getCorrelationId(), e);
                context.fail(IngestionEvent.CODE_HANDLER_FAILED,
                        "Handler '" + handler.name() + "' failed: " + e.getMessage());
                return;
            }
        }
        if (context.isFailed()) {
            return;
        }
        if (context.isSkipped()) {
            log.debug("Skipped {} [run {}]",
                    context.sourceFile().path(), context.run().getCorrelationId());
        }
    }

    /** The handler names in execution order, for diagnostics and the studio API. */
    public List<String> handlerNames() {
        return handlers.stream().map(IngestionHandler::name).toList();
    }

    /** Whether the pipeline recorded anything an author should see. */
    public static boolean hasAuthorFacingDiagnostics(IngestionContext context) {
        return context.diagnostics().stream()
                .anyMatch(event -> event.getSeverity() != IngestionSeverity.INFO);
    }
}

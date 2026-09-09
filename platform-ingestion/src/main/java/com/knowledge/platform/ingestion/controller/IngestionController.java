package com.knowledge.platform.ingestion.controller;

import com.knowledge.platform.ingestion.delegate.IngestionDelegate;
import com.knowledge.platform.ingestion.model.response.IngestionRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Studio visibility into materialization.
 *
 * <p>Exists so that "why did my article not appear" is answerable from the product rather than from a
 * log file. Each run reports what it processed and every diagnostic it produced -- unresolved links,
 * missing titles, dropped HTML -- against the source path that caused it.
 */
@RestController
@RequestMapping("/api/studio/ingestions")
@Validated
@Tag(name = "Studio: Ingestion", description = "Materialization runs and their diagnostics")
public class IngestionController {

    private final IngestionDelegate ingestionDelegate;

    public IngestionController(IngestionDelegate ingestionDelegate) {
        this.ingestionDelegate = ingestionDelegate;
    }

    @GetMapping("/{id}")
    @Operation(summary = "One ingestion run, with every diagnostic it produced")
    public IngestionRunResponse get(@PathVariable UUID id) {
        return ingestionDelegate.get(id);
    }

    @GetMapping
    @Operation(summary = "Recent ingestion runs, without diagnostics")
    public List<IngestionRunResponse> recent(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) UUID repositoryId) {
        return repositoryId == null
                ? ingestionDelegate.recent(limit)
                : ingestionDelegate.forRepository(repositoryId, limit);
    }

    @GetMapping("/pipeline")
    @Operation(summary = "The configured handler chain, in execution order",
            description = "Reports the pipeline as actually assembled, not as documented.")
    public List<String> pipeline() {
        return ingestionDelegate.pipeline();
    }
}

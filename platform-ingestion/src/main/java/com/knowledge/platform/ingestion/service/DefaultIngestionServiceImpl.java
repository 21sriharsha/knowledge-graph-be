package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.common.service.ContentHasher;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.IngestionChain;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.source.integration.adapter.SourceAdapterException;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.service.SourceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Default IngestionService.
 *
 * <p>See {@link IngestionService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultIngestionServiceImpl implements IngestionService {

    /** Put on the logging context so every line from a run carries its correlation id. */
    private static final String MDC_CORRELATION_ID = "ingestionCorrelationId";

    private final SourceService sourceService;
    private final IngestionChain ingestionChain;
    private final IngestionRunRecorder runRecorder;
    private final ArticleWithdrawalService withdrawalService;
    private final ArticleService articleService;
    private final ContentHasher contentHasher;
    private final MeterRegistry meterRegistry;

    public DefaultIngestionServiceImpl(
            SourceService sourceService,
            IngestionChain ingestionChain,
            IngestionRunRecorder runRecorder,
            ArticleWithdrawalService withdrawalService,
            ArticleService articleService,
            ContentHasher contentHasher,
            MeterRegistry meterRegistry) {
        this.sourceService = sourceService;
        this.ingestionChain = ingestionChain;
        this.runRecorder = runRecorder;
        this.withdrawalService = withdrawalService;
        this.articleService = articleService;
        this.contentHasher = contentHasher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public IngestionRun ingest(SourceSyncRequestedEvent event) {
        SourceRepository repository = sourceService.requireById(event.repositoryId());
        IngestionRun run = runRecorder.begin(repository.getId(), event);

        MDC.put(MDC_CORRELATION_ID, run.getCorrelationId());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<SourceFile> files = fetchFiles(repository, event);
            log.info("Ingesting {} document(s) from {} at revision {}",
                    files.size(), repository, event.revision());

            files.forEach(file -> processDocument(repository, run, file));
            processRemovals(repository, run, event.removedPaths());

            run.finish();
            sourceService.recordSync(repository.getId(), event.revision());
            sample.stop(meterRegistry.timer("knowledge.ingestion.run",
                    "outcome", run.getState().name()));
            log.info("Ingestion finished: {} succeeded, {} skipped, {} failed",
                    run.getDocumentsSucceeded(), run.getDocumentsSkipped(), run.getDocumentsFailed());
            return runRecorder.save(run);

        } catch (SourceAdapterException e) {
            // A provider outage must not corrupt canonical content. Nothing has been written at this
            // point; the failure is recorded and the next sync starts from the same place.
            log.error("Provider unavailable during ingestion of {}", repository, e);
            return failRun(run, sample, IngestionEvent.CODE_PROVIDER_ERROR,
                    "Provider unavailable: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Ingestion of {} failed unexpectedly", repository, e);
            return failRun(run, sample, IngestionEvent.CODE_HANDLER_FAILED,
                    "Ingestion failed: " + e.getMessage());
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private List<SourceFile> fetchFiles(SourceRepository repository, SourceSyncRequestedEvent event) {
        if (event.fullResync() || event.changedPaths().isEmpty()) {
            return sourceService.fetchContentSnapshot(repository, event.revision());
        }

        List<SourceFile> files = new ArrayList<>();
        for (String path : event.changedPaths()) {
            Optional<SourceFile> file = sourceService.fetchFile(repository, path, event.revision());
            if (file.isPresent()) {
                files.add(file.get());
            } else {
                // Expected, not exceptional: the push touched a file that is not Markdown, sits
                // outside the content path, or was deleted later within the same push.
                log.debug("Skipping {} at {}: not retrievable as content", path, event.revision());
            }
        }
        return files;
    }

    private void processDocument(SourceRepository repository, IngestionRun run, SourceFile file) {
        IngestionContext context = new IngestionContext(
                run, repository, file, contentHasher.hash(file.content()));

        ingestionChain.execute(context);
        runRecorder.recordDiagnostics(context);

        if (context.isFailed()) {
            run.recordDocument(IngestionRun.DocumentOutcome.FAILED);
        } else if (context.isSkipped()) {
            run.recordDocument(IngestionRun.DocumentOutcome.SKIPPED);
        } else {
            run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
        }
    }

    private void processRemovals(
            SourceRepository repository, IngestionRun run, List<String> removedPaths) {
        if (removedPaths.isEmpty()) {
            return;
        }
        // One lookup for the repository's articles rather than one per removed path.
        var byPath = articleService.findByRepository(repository.getId());
        for (String path : removedPaths) {
            byPath.stream()
                    .filter(article -> path.equals(article.getSourcePath()))
                    .findFirst()
                    .ifPresent(article -> {
                        withdrawalService.withdraw(article);
                        runRecorder.recordEvent(run, path, IngestionSeverity.INFO,
                                IngestionEvent.CODE_ARTICLE_REMOVED,
                                "Source file was deleted; the article is archived and links to it "
                                        + "are now unresolved");
                        run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
                    });
        }
    }

    private IngestionRun failRun(
            IngestionRun run, Timer.Sample sample, String code, String message) {
        runRecorder.recordEvent(run, null, IngestionSeverity.ERROR, code, message);
        run.fail();
        sample.stop(meterRegistry.timer("knowledge.ingestion.run", "outcome", "FAILED"));
        return runRecorder.save(run);
    }
}

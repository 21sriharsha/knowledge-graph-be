package com.knowledge.platform.ingestion.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a run reports itself decides whether an operator trusts the status at all, so the steady-state
 * cases matter as much as the failure ones.
 */
class IngestionRunTest {

    @Test
    void reportsSuccessWhenNothingFailed() {
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
        run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
        run.finish();

        assertThat(run.getState()).isEqualTo(IngestionState.SUCCEEDED);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("a run that processed nothing is a success: a commit touching no Markdown is a no-op")
    void reportsSuccessForAnEmptyRun() {
        IngestionRun run = start();
        run.finish();

        assertThat(run.getState()).isEqualTo(IngestionState.SUCCEEDED);
    }

    @Test
    @DisplayName("a re-sync where everything was already current is a success, not a failure")
    void treatsSkippedDocumentsAsHealthy() {
        // The normal steady state: nothing changed since the last run. Counting only "succeeded" as
        // healthy would report this as FAILED and train operators to ignore the status entirely.
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.SKIPPED);
        run.recordDocument(IngestionRun.DocumentOutcome.SKIPPED);
        run.finish();

        assertThat(run.getState()).isEqualTo(IngestionState.SUCCEEDED);
    }

    @Test
    @DisplayName("one bad file among many is partial success, not a failed sync")
    void reportsPartialSuccessWhenSomeDocumentsFailed() {
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
        run.recordDocument(IngestionRun.DocumentOutcome.FAILED);
        run.finish();

        assertThat(run.getState()).isEqualTo(IngestionState.PARTIALLY_SUCCEEDED);
    }

    @Test
    @DisplayName("a skipped document alongside a failure is still partial success")
    void countsSkippedTowardPartialSuccess() {
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.SKIPPED);
        run.recordDocument(IngestionRun.DocumentOutcome.FAILED);
        run.finish();

        assertThat(run.getState()).isEqualTo(IngestionState.PARTIALLY_SUCCEEDED);
    }

    @Test
    void reportsFailureWhenEveryDocumentFailed() {
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.FAILED);
        run.recordDocument(IngestionRun.DocumentOutcome.FAILED);
        run.finish();

        assertThat(run.getState()).isEqualTo(IngestionState.FAILED);
    }

    @Test
    void countsEachOutcomeSeparately() {
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
        run.recordDocument(IngestionRun.DocumentOutcome.SKIPPED);
        run.recordDocument(IngestionRun.DocumentOutcome.FAILED);
        run.finish();

        assertThat(run.getDocumentsTotal()).isEqualTo(3);
        assertThat(run.getDocumentsSucceeded()).isEqualTo(1);
        assertThat(run.getDocumentsSkipped()).isEqualTo(1);
        assertThat(run.getDocumentsFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("a correlation id exists before the row is written, so it can be logged either way")
    void carriesACorrelationIdFromCreation() {
        IngestionRun run = start();

        assertThat(run.getCorrelationId()).isNotBlank().hasSize(32);
        assertThat(run.getState()).isEqualTo(IngestionState.RUNNING);
        assertThat(run.getFinishedAt()).isNull();
    }

    @Test
    void failClosesTheRunOutright() {
        IngestionRun run = start();
        run.recordDocument(IngestionRun.DocumentOutcome.SUCCEEDED);
        run.fail();

        assertThat(run.getState()).isEqualTo(IngestionState.FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    private IngestionRun start() {
        return IngestionRun.start(UUID.randomUUID(),
                SourceSyncRequestedEvent.Trigger.WEBHOOK, "rev-1");
    }
}

package com.knowledge.platform.ingestion.pipeline;

/**
 * One step of the materialization pipeline.
 *
 * <p>Each handler has a single responsibility and communicates only through {@link IngestionContext}.
 * That is what keeps the chain from becoming the god-class the specification warns against: a handler
 * cannot reach into another, and adding a step means adding a class rather than editing a method.
 *
 * <p>Handlers are ordered by {@link HandlerOrder} and discovered by Spring, so a new step is inserted
 * by declaring it -- no existing handler is rewritten, and no central list has to be maintained.
 */
public interface IngestionHandler {

    /** A short name used in diagnostics and metrics. */
    String name();

    /**
     * Processes the document.
     *
     * <p>A handler that determines the document needs no further work calls
     * {@link IngestionContext#skip}; one that cannot proceed calls {@link IngestionContext#fail}.
     * Either stops the chain for that document without stopping the run.
     */
    void handle(IngestionContext context);

    /**
     * Whether this handler applies at all.
     *
     * <p>Defaults to running only while the chain is live, which is what most handlers want. A handler
     * that must run even for a skipped or failed document -- diagnostics persistence, say -- overrides
     * this.
     */
    default boolean appliesTo(IngestionContext context) {
        return context.shouldContinue();
    }
}

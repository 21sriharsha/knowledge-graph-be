package com.knowledge.platform.ingestion.model.entity;

/** The lifecycle of a materialization run. */
public enum IngestionState {
    RUNNING,
    SUCCEEDED,
    /**
     * Some documents materialized and some did not.
     *
     * <p>Distinct from FAILED on purpose. One malformed file in a repository of two hundred must not
     * be reported as a failed sync, and an operator needs to be able to tell the two apart at a
     * glance.
     */
    PARTIALLY_SUCCEEDED,
    FAILED
}

package com.knowledge.platform.ingestion.model.entity;

/** How serious an ingestion diagnostic is. */
public enum IngestionSeverity {
    /** Something worth recording that did not affect the outcome. */
    INFO,
    /** The document was materialized, but something an author should know about happened. */
    WARNING,
    /** The document was not materialized. */
    ERROR
}

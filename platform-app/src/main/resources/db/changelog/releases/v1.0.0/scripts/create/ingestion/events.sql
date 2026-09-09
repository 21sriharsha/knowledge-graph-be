-- Structured ingestion diagnostics.
--
-- An unresolved [[link]], a missing frontmatter title or a provider error is recorded here rather
-- than logged and forgotten. This table is what lets the studio answer "why is my article not
-- showing what I expect" without anyone reading a log file.
CREATE TABLE ingestion.events (
    id           UUID          NOT NULL,
    run_id       UUID          NOT NULL,
    source_path  VARCHAR(1000),
    article_slug VARCHAR(160),
    severity     VARCHAR(16)   NOT NULL,
    code         VARCHAR(64)   NOT NULL,
    message      TEXT          NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_run FOREIGN KEY (run_id) REFERENCES ingestion.runs (id) ON DELETE CASCADE,
    CONSTRAINT ck_events_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR'))
);

CREATE INDEX idx_events_run ON ingestion.events (run_id, occurred_at);

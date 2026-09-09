-- Persisted article read models.
--
-- Two levels of avoidance, answering different questions (North Star section 14): the Caffeine cache
-- avoids hitting the database at all, and this table avoids re-assembling the model from normalised
-- tables when the cache is cold. A process restart therefore costs one indexed read per article
-- rather than a full reassembly with its graph and related-content queries.
--
-- JSONB rather than a column per field: the shape is a frontend contract that will evolve, and the
-- backend never queries into it -- it is always fetched whole by primary key.
CREATE TABLE read_model.article_read_models (
    article_id   UUID        NOT NULL,
    payload      JSONB       NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_article_read_models PRIMARY KEY (article_id),
    CONSTRAINT fk_article_read_models_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE
);

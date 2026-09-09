-- Platform- and AI-proposed edges.
--
-- Deliberately a separate table from article_links, not a link_type on it. A suggestion must never
-- be capable of being mistaken for authorial intent, and a schema separation makes that structural:
-- a query over author-written links cannot accidentally include suggestions.
CREATE TABLE graph.suggested_relationships (
    id                UUID             NOT NULL,
    source_article_id UUID             NOT NULL,
    target_article_id UUID             NOT NULL,
    relationship_kind VARCHAR(32)      NOT NULL,
    score             DOUBLE PRECISION NOT NULL,
    generated_by      VARCHAR(64)      NOT NULL,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT pk_suggested_relationships PRIMARY KEY (id),
    CONSTRAINT fk_suggested_source
        FOREIGN KEY (source_article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_suggested_target
        FOREIGN KEY (target_article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT uq_suggested_edge
        UNIQUE (source_article_id, target_article_id, relationship_kind),
    CONSTRAINT ck_suggested_kind
        CHECK (relationship_kind IN ('SHARED_TOPIC', 'SHARED_TAG', 'SEMANTIC_SIMILARITY')),
    CONSTRAINT ck_suggested_not_self CHECK (source_article_id <> target_article_id),
    CONSTRAINT ck_suggested_score CHECK (score >= 0.0 AND score <= 1.0)
);

CREATE INDEX idx_suggested_source_score
    ON graph.suggested_relationships (source_article_id, score DESC);

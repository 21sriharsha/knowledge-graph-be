-- Derived lexical search representation, one row per article.
--
-- Kept out of content.articles so that a reindex never rewrites canonical content, and so the whole
-- table can be dropped and rebuilt from Markdown without touching the source of truth.
CREATE TABLE search.article_search_documents (
    article_id    UUID        NOT NULL,
    title_text    TEXT        NOT NULL,
    summary_text  TEXT        NOT NULL DEFAULT '',
    body_text     TEXT        NOT NULL,
    metadata_text TEXT        NOT NULL DEFAULT '',
    content_hash  VARCHAR(64) NOT NULL,
    indexed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Generated rather than application-maintained. The field weighting is a property of the index,
    -- so putting it in the schema is what stops the indexing path and the query path from drifting
    -- apart -- there is no second place to express it.
    --
    -- A: title, B: summary, C: author/tag/topic metadata, D: body.
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', COALESCE(title_text, '')),    'A') ||
        setweight(to_tsvector('english', COALESCE(summary_text, '')),  'B') ||
        setweight(to_tsvector('english', COALESCE(metadata_text, '')), 'C') ||
        setweight(to_tsvector('english', COALESCE(body_text, '')),     'D')
    ) STORED,

    CONSTRAINT pk_article_search_documents PRIMARY KEY (article_id),
    CONSTRAINT fk_article_search_documents_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE
);

CREATE INDEX idx_article_search_vector
    ON search.article_search_documents USING GIN (search_vector);

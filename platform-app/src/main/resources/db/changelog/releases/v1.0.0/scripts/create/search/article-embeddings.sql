-- Derived semantic representation.
--
-- DIMENSION: 768 matches nomic-embed-text, the default local Ollama embedding model. pgvector fixes
-- the dimension per column and the index is built over it, so changing embedding model is a
-- migration in a new release, not a configuration change. The CHECK makes a mismatched write fail
-- at insert rather than produce silently unusable vectors.
--
-- Content hash records what was embedded, so staleness is detectable when an article is edited and
-- the embedding job has not yet caught up.
CREATE TABLE search.article_embeddings (
    article_id   UUID         NOT NULL,
    model        VARCHAR(200) NOT NULL,
    dimensions   INTEGER      NOT NULL,
    embedding    VECTOR(768)  NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_article_embeddings PRIMARY KEY (article_id),
    CONSTRAINT fk_article_embeddings_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT ck_article_embeddings_dimensions CHECK (dimensions = 768)
);

-- HNSW over cosine distance. Chosen over IVFFlat because IVFFlat needs a training pass over
-- existing rows to build useful lists, and this corpus starts empty -- an index built at migration
-- time would be trained on nothing.
CREATE INDEX idx_article_embeddings_hnsw
    ON search.article_embeddings USING hnsw (embedding vector_cosine_ops);

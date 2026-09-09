-- Canonical article content. Every row in the graph, search and read_model schemas is derived from
-- this table and can be rebuilt from it.
CREATE TABLE content.articles (
    id                   UUID          NOT NULL,
    slug                 VARCHAR(160)  NOT NULL,
    title                VARCHAR(500)  NOT NULL,
    summary              TEXT,
    canonical_markdown   TEXT          NOT NULL,
    content_hash         VARCHAR(64)   NOT NULL,
    publication_state    VARCHAR(32)   NOT NULL,
    author_id            UUID          NOT NULL,
    primary_topic_id     UUID,
    repository_id        UUID,
    source_path          VARCHAR(1000),
    source_revision      VARCHAR(200),
    revision_number      INTEGER       NOT NULL DEFAULT 1,
    word_count           INTEGER       NOT NULL DEFAULT 0,
    reading_time_minutes INTEGER       NOT NULL DEFAULT 0,
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_articles PRIMARY KEY (id),
    CONSTRAINT uq_articles_slug UNIQUE (slug),
    CONSTRAINT fk_articles_author FOREIGN KEY (author_id) REFERENCES author.authors (id),
    CONSTRAINT fk_articles_primary_topic
        FOREIGN KEY (primary_topic_id) REFERENCES content.topics (id) ON DELETE SET NULL,
    CONSTRAINT fk_articles_repository
        FOREIGN KEY (repository_id) REFERENCES source.repositories (id) ON DELETE SET NULL,
    CONSTRAINT ck_articles_publication_state
        CHECK (publication_state IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_articles_published_at
        CHECK (publication_state <> 'PUBLISHED' OR published_at IS NOT NULL)
);

-- (repository, path) is the article's stable identity, and it is what makes a title rename a
-- non-event: ingestion looks the article up by its source coordinate before it looks at the slug,
-- so retitling updates a row instead of creating a second one and orphaning inbound links.
CREATE UNIQUE INDEX uq_articles_source_coordinate
    ON content.articles (repository_id, source_path)
    WHERE repository_id IS NOT NULL AND source_path IS NOT NULL;

-- The public listing query: published articles, newest first. Partial, because nothing on the read
-- path ever lists drafts.
CREATE INDEX idx_articles_published
    ON content.articles (published_at DESC)
    WHERE publication_state = 'PUBLISHED';

-- Trigram index for the "did you mean" and slug-suggestion paths, which are prefix/fuzzy rather
-- than full-text and therefore not served by the search schema's tsvector.
CREATE INDEX idx_articles_title_trgm ON content.articles USING GIN (title gin_trgm_ops);

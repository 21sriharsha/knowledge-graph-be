-- Revision history of canonical Markdown.
CREATE TABLE content.article_revisions (
    id              UUID         NOT NULL,
    article_id      UUID         NOT NULL,
    revision_number INTEGER      NOT NULL,
    title           VARCHAR(500) NOT NULL,
    markdown        TEXT         NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    source_revision VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_article_revisions PRIMARY KEY (id),
    CONSTRAINT fk_article_revisions_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT uq_article_revisions_number UNIQUE (article_id, revision_number),

    -- Idempotency enforced by the schema rather than by pipeline discipline: a replayed webhook
    -- carrying content already stored cannot append a duplicate revision, whatever the caller does.
    CONSTRAINT uq_article_revisions_hash UNIQUE (article_id, content_hash)
);

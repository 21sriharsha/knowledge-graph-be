-- Author-written links: [[Target]], [[Target|alias]] and internal Markdown links.
--
-- Derived, but authoritative in a way suggestions are not: these edges express what the author
-- actually wrote. A reference whose target does not exist is kept with resolution_state UNRESOLVED
-- rather than discarded, for two reasons -- ingestion can report it as a diagnostic, and the edge
-- repairs itself when the target is finally published.
CREATE TABLE graph.article_links (
    id                UUID          NOT NULL,
    source_article_id UUID          NOT NULL,
    target_article_id UUID,
    target_reference  VARCHAR(500)  NOT NULL,
    target_slug       VARCHAR(160)  NOT NULL,
    display_text      VARCHAR(500),
    link_type         VARCHAR(32)   NOT NULL,
    resolution_state  VARCHAR(32)   NOT NULL,
    ordinal           INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_article_links PRIMARY KEY (id),
    CONSTRAINT fk_article_links_source
        FOREIGN KEY (source_article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_links_target
        FOREIGN KEY (target_article_id) REFERENCES content.articles (id) ON DELETE SET NULL,
    CONSTRAINT uq_article_links_edge UNIQUE (source_article_id, target_slug, ordinal),
    CONSTRAINT ck_article_links_type CHECK (link_type IN ('INTERNAL_WIKI', 'INTERNAL_SLUG')),
    CONSTRAINT ck_article_links_resolution CHECK (resolution_state IN ('RESOLVED', 'UNRESOLVED')),
    CONSTRAINT ck_article_links_resolved_has_target
        CHECK (resolution_state = 'UNRESOLVED' OR target_article_id IS NOT NULL)
);

-- Backlinks ("what points at this article") are a first-class read-model field, so the reverse
-- direction needs its own index.
CREATE INDEX idx_article_links_target ON graph.article_links (target_article_id);

-- Publishing an article means repairing every edge that was waiting for its slug. Partial, because
-- resolved edges are the overwhelming majority and never need this lookup.
CREATE INDEX idx_article_links_unresolved
    ON graph.article_links (target_slug)
    WHERE resolution_state = 'UNRESOLVED';

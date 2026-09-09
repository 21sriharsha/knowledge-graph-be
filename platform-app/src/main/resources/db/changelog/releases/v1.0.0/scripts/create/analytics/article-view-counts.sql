-- Readership counts, bucketed by day.
--
-- A third kind of schema, and the reason it is not folded into either existing kind: view counts are
-- neither canonical content nor derived from it. They cannot be rebuilt by re-running ingestion --
-- losing them is losing them -- so they must not sit in `read_model`, whose defining property is
-- that a full rematerialization is a TRUNCATE. Nor are they authored, so they do not belong beside
-- articles in `content`. Observed, durable, and not reconstructible: its own schema.
CREATE SCHEMA IF NOT EXISTS analytics;

-- One row per article per day rather than one row per view.
--
-- A row per view would grow without bound to answer a question nobody asks -- nothing here needs to
-- know about an individual read, only how many there were. Daily buckets keep the table proportional
-- to articles times days, make a windowed "last 7 days" a range scan, and are additive, so several
-- backend instances can each flush their own counts into the same row without coordination.
--
-- Deliberately no IP address, user agent, session or reader identity. This table cannot answer who
-- read what, only how often something was read, and that is the whole of what the landing page asks.
CREATE TABLE analytics.article_view_counts (
    article_id UUID   NOT NULL,
    view_date  DATE   NOT NULL,
    views      BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_article_view_counts PRIMARY KEY (article_id, view_date),
    CONSTRAINT fk_article_view_counts_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT ck_article_view_counts_views CHECK (views >= 0)
);

-- Trending is always "the last N days", so the window is the leading predicate.
CREATE INDEX idx_article_view_counts_window
    ON analytics.article_view_counts (view_date DESC, article_id);

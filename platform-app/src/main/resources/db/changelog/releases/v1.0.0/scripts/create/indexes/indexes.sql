-- PostgreSQL does not automatically index foreign key columns. Without an index, every join on the
-- column is a sequential scan and every parent DELETE scans the child table.
--
-- Only FK columns that are NOT already covered by a primary key or unique constraint are listed
-- here; each index is a write cost paid on every insert and update, so this is a deliberately short
-- list rather than blanket coverage. Indexes that belong to a single table's own access pattern are
-- declared beside that table instead of here.
--
-- Already covered, intentionally omitted:
--   source.repositories.owner_author_id       -> low cardinality, tiny table, never joined hot
--   content.article_revisions.article_id      -> leading column of uq_article_revisions_number
--   content.article_tags.article_id           -> leading column of pk_article_tags
--   content.article_topics.article_id         -> leading column of pk_article_topics
--   source.webhook_deliveries.repository_id   -> leading column of uq_webhook_deliveries
--   ingestion.runs.repository_id              -> leading column of idx_runs_repository_started
--   ingestion.events.run_id                   -> leading column of idx_events_run
--   graph.article_links.source_article_id     -> leading column of uq_article_links_edge
--   graph.article_links.target_article_id     -> idx_article_links_target
--   graph.suggested_relationships.source_...  -> leading column of uq_suggested_edge
--   search.*.article_id, read_model.*.article_id -> primary keys

-- content.articles.author_id: every author profile page lists that author's articles, and the
-- search planner's author dimension filters on it.
CREATE INDEX idx_articles_author ON content.articles (author_id);

-- content.articles.primary_topic_id: topic landing pages.
CREATE INDEX idx_articles_primary_topic ON content.articles (primary_topic_id);

-- content.article_tags.tag_id / article_topics.topic_id: the reverse direction of the join, used by
-- tag and topic listing pages. The primary key only covers the article_id direction.
CREATE INDEX idx_article_tags_tag ON content.article_tags (tag_id);
CREATE INDEX idx_article_topics_topic ON content.article_topics (topic_id);

-- graph.suggested_relationships.target_article_id: not covered by uq_suggested_edge, whose leading
-- column is the source. Needed to purge suggestions pointing at an article being rebuilt.
CREATE INDEX idx_suggested_target ON graph.suggested_relationships (target_article_id);

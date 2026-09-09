-- Records which payload shape a stored read model was written under.
--
-- The content hash answers "is this built from the current revision of the article"; it cannot
-- answer "is this built by the current version of the assembler". When the read model gained a
-- field, every stored payload was still hash-current and so was served indefinitely, silently
-- missing the new field until the author happened to edit the article. Versioning the shape makes
-- that staleness detectable, and a bump re-materializes the payload on next read.
--
-- Existing rows are backfilled to 1, the shape they were written under.
ALTER TABLE read_model.article_read_models
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;

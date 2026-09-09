-- Public URL path to target resolution.
--
-- Materialized so that the delivery module answers a route lookup with one indexed read, instead of
-- guessing which entity type a path belongs to by trying each repository in turn.
CREATE TABLE read_model.routes (
    path        VARCHAR(500) NOT NULL,
    target_type VARCHAR(32)  NOT NULL,
    target_id   UUID         NOT NULL,
    target_slug VARCHAR(160) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_routes PRIMARY KEY (path),
    CONSTRAINT ck_routes_target_type CHECK (target_type IN ('ARTICLE', 'AUTHOR', 'TOPIC', 'TAG'))
);

-- Invalidating an entity's routes requires finding them by what they point at, not by path.
CREATE INDEX idx_routes_target ON read_model.routes (target_type, target_id);

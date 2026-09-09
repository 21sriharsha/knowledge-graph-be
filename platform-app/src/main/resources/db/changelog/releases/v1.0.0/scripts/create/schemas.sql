-- Schemas are logical module boundaries, mirroring the modules declared in CLAUDE.md. They are not
-- tenancy boundaries: every schema is shared.
--
-- The split runs along the North Star's Invariant 8. Canonical schemas hold authoritative content;
-- derived schemas hold representations materialized from it and are safe to truncate and rebuild.

-- Canonical: the source of truth. Losing any of this is data loss.
CREATE SCHEMA IF NOT EXISTS author;
CREATE SCHEMA IF NOT EXISTS content;
CREATE SCHEMA IF NOT EXISTS source;
CREATE SCHEMA IF NOT EXISTS ingestion;

-- Derived: rebuildable from the canonical schemas by re-running the ingestion pipeline.
-- A full rematerialization is a TRUNCATE of exactly these three.
CREATE SCHEMA IF NOT EXISTS graph;
CREATE SCHEMA IF NOT EXISTS search;
CREATE SCHEMA IF NOT EXISTS read_model;

-- Liquibase's own tracking tables stay in `public`, which therefore holds migration bookkeeping and
-- nothing else. They cannot live in a schema created here: Liquibase writes its changelog table
-- before it runs the first changeset, so pointing it at a schema this file creates is a bootstrap
-- cycle. The convention that follows is simple and checkable -- no platform table is ever in
-- `public`, and everything in `public` is Liquibase's.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The migrations, checked against a real PostgreSQL.
 *
 * <p>Worth testing directly rather than trusting the application to start: Hibernate's
 * {@code ddl-auto: validate} confirms the mappings agree with the schema, but says nothing about the
 * things only PostgreSQL enforces -- the generated {@code tsvector}, the partial indexes, the
 * expression-based unique index, and the check constraints the domain relies on.
 */
@EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
        disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
class MigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("every module schema exists, and Liquibase's bookkeeping stays out of them")
    void createsTheModuleSchemas() {
        List<String> schemas = jdbcTemplate.queryForList("""
                select nspname from pg_namespace
                 where nspname not like 'pg_%' and nspname <> 'information_schema'
                 order by nspname
                """, String.class);

        assertThat(schemas).contains(
                "author", "content", "source", "ingestion", "graph", "search", "read_model");
    }

    @Test
    @DisplayName("no platform table lives in public; public holds migration bookkeeping only")
    void keepsPublicForLiquibaseAlone() {
        List<String> publicTables = jdbcTemplate.queryForList("""
                select tablename from pg_tables where schemaname = 'public' order by tablename
                """, String.class);

        assertThat(publicTables)
                .allSatisfy(table -> assertThat(table).startsWith("databasechangelog"));
    }

    @Test
    void appliesEveryChangesetInTheRelease() {
        List<String> applied = jdbcTemplate.queryForList(
                "select id from public.databasechangelog order by orderexecuted", String.class);

        assertThat(applied).containsExactly(
                "release-tag-v1.0.0", "schema-setup", "author-setup", "source-setup",
                "content-setup", "ingestion-setup", "graph-setup", "search-setup",
                "read-model-setup", "read-model-schema-version", "analytics-setup",
                "index-setup");
    }

    @Test
    @DisplayName("the release tag is recorded, so a rollback has a named anchor to return to")
    void recordsTheReleaseTag() {
        String tag = jdbcTemplate.queryForObject(
                "select tag from public.databasechangelog where tag is not null limit 1", String.class);

        assertThat(tag).isEqualTo("v1.0.0");
    }

    @Test
    void installsTheRequiredExtensions() {
        List<String> extensions = jdbcTemplate.queryForList(
                "select extname from pg_extension order by extname", String.class);

        assertThat(extensions).contains("vector", "pg_trgm", "unaccent");
    }

    @Test
    @DisplayName("the search vector is a generated column, so indexing and querying cannot drift")
    void generatesTheSearchVector() {
        String generated = jdbcTemplate.queryForObject("""
                select is_generated from information_schema.columns
                 where table_schema = 'search'
                   and table_name = 'article_search_documents'
                   and column_name = 'search_vector'
                """, String.class);

        assertThat(generated).isEqualTo("ALWAYS");
    }

    @Test
    void createsTheIndexesTheReadAndSearchPathsDependOn() {
        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname from pg_indexes
                 where schemaname in ('content', 'search', 'graph', 'read_model')
                 order by indexname
                """, String.class);

        assertThat(indexes).contains(
                "idx_article_search_vector",        // GIN over the tsvector
                "idx_article_embeddings_hnsw",      // HNSW over cosine distance
                "idx_articles_published",           // partial: published articles, newest first
                "idx_article_links_unresolved",     // partial: repairing dangling references
                "uq_articles_source_coordinate");   // the stable identity across renames
    }

    @Test
    @DisplayName("a published article without a publication date is rejected by the database")
    void enforcesThePublishedAtConstraint() {
        jdbcTemplate.update("""
                insert into author.authors (id, slug, display_name)
                values ('11111111-1111-1111-1111-111111111111', 'constraint-author', 'Author')
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into content.articles
                            (id, slug, title, canonical_markdown, content_hash, publication_state, author_id)
                        values ('22222222-2222-2222-2222-222222222222', 'no-date', 'No Date',
                                '# No Date', 'hash', 'PUBLISHED',
                                '11111111-1111-1111-1111-111111111111')
                        """))
                .hasMessageContaining("ck_articles_published_at");
    }

    @Test
    @DisplayName("a resolved link must name a target; the schema will not store a contradiction")
    void enforcesTheLinkResolutionConstraint() {
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.check_constraints
                 where constraint_name = 'ck_article_links_resolved_has_target'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("the embedding dimension is fixed by the schema, not by configuration")
    void pinsTheEmbeddingDimension() {
        String definition = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                 where conname = 'ck_article_embeddings_dimensions'
                """, String.class);

        assertThat(definition).contains("768");
    }
}

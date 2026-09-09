package com.knowledge.platform.search.repository;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchFilters;
import com.knowledge.platform.search.model.entity.ArticleSearchDocument;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The lexical index, accessed through JDBC.
 *
 * <p>Every query here is parameterized. That is worth stating explicitly in a module where a language
 * model is involved: the model produces a typed {@link com.knowledge.platform.ai.model.dto.SearchIntent},
 * the planner turns it into typed filters, and those arrive here as bind parameters. There is no path
 * by which model output becomes SQL text -- the {@code IN} clauses below build placeholders from list
 * sizes and never from any value.
 */
@Repository
public class ArticleSearchDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArticleSearchDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts or replaces an article's search document. */
    public void save(ArticleSearchDocument document) {
        jdbcTemplate.update("""
                        insert into search.article_search_documents
                            (article_id, title_text, summary_text, body_text, metadata_text,
                             content_hash, indexed_at)
                        values (?, ?, ?, ?, ?, ?, now())
                        on conflict (article_id) do update set
                            title_text    = excluded.title_text,
                            summary_text  = excluded.summary_text,
                            body_text     = excluded.body_text,
                            metadata_text = excluded.metadata_text,
                            content_hash  = excluded.content_hash,
                            indexed_at    = now()
                        """,
                document.articleId(), document.titleText(), nullToEmpty(document.summaryText()),
                document.bodyText(), nullToEmpty(document.metadataText()), document.contentHash());
    }

    public void delete(UUID articleId) {
        jdbcTemplate.update(
                "delete from search.article_search_documents where article_id = ?", articleId);
    }

    public boolean isIndexedAt(UUID articleId, String contentHash) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from search.article_search_documents
                 where article_id = ? and content_hash = ?
                """, Integer.class, articleId, contentHash);
        return count != null && count > 0;
    }

    /**
     * Full-text retrieval over published articles.
     *
     * <p>{@code websearch_to_tsquery} rather than {@code plainto_tsquery}: it understands quoted
     * phrases and {@code -exclusions}, which readers type without being told they can, and it never
     * raises a syntax error on arbitrary input the way {@code to_tsquery} does.
     *
     * <p>{@code ts_rank_cd} rather than {@code ts_rank}: cover density accounts for how close the
     * matched terms are to each other, which is what distinguishes an article about Kubernetes
     * networking from one that mentions Kubernetes in the intro and networking in the footnotes.
     */
    public List<RetrievalCandidate> searchFullText(String queryText, SearchFilters filters, int limit) {
        StringBuilder sql = new StringBuilder("""
                select d.article_id,
                       ts_rank_cd(d.search_vector, websearch_to_tsquery('english', ?)) as rank
                  from search.article_search_documents d
                  join content.articles a on a.id = d.article_id
                 where a.publication_state = 'PUBLISHED'
                   and d.search_vector @@ websearch_to_tsquery('english', ?)
                """);

        List<Object> arguments = new ArrayList<>();
        arguments.add(queryText);
        arguments.add(queryText);
        appendFilters(sql, arguments, filters);
        sql.append(" order by rank desc limit ?");
        arguments.add(limit);

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new RetrievalCandidate(
                        rs.getObject("article_id", UUID.class),
                        rs.getDouble("rank"),
                        RetrievalCandidate.Source.FULL_TEXT),
                arguments.toArray());
    }

    /**
     * Metadata-only retrieval, for a query that is entirely filters ("everything by Harsh on
     * Kubernetes") and therefore has no text to rank by.
     *
     * <p>Ordered by recency, since with no query text there is no relevance signal to order by and
     * pretending otherwise would just be arbitrary.
     */
    public List<RetrievalCandidate> searchByMetadata(SearchFilters filters, int limit) {
        StringBuilder sql = new StringBuilder("""
                select a.id as article_id, a.published_at
                  from content.articles a
                 where a.publication_state = 'PUBLISHED'
                """);

        List<Object> arguments = new ArrayList<>();
        appendFilters(sql, arguments, filters);
        sql.append(" order by a.published_at desc limit ?");
        arguments.add(limit);

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new RetrievalCandidate(
                        rs.getObject("article_id", UUID.class),
                        1.0,
                        RetrievalCandidate.Source.METADATA),
                arguments.toArray());
    }

    /**
     * Appends the metadata predicates shared by every retrieval query.
     *
     * <p>Placeholders are generated from list sizes; values only ever arrive as bind parameters.
     */
    void appendFilters(StringBuilder sql, List<Object> arguments, SearchFilters filters) {
        if (!filters.authorIds().isEmpty()) {
            sql.append(" and a.author_id in (").append(placeholders(filters.authorIds().size())).append(")");
            arguments.addAll(filters.authorIds());
        }
        if (!filters.topicSlugs().isEmpty()) {
            sql.append("""
                     and exists (select 1 from content.article_topics at
                                   join content.topics t on t.id = at.topic_id
                                  where at.article_id = a.id and t.slug in (\
                    """)
                    .append(placeholders(filters.topicSlugs().size())).append("))");
            arguments.addAll(filters.topicSlugs());
        }
        if (!filters.tagSlugs().isEmpty()) {
            sql.append("""
                     and exists (select 1 from content.article_tags at
                                   join content.tags t on t.id = at.tag_id
                                  where at.article_id = a.id and t.slug in (\
                    """)
                    .append(placeholders(filters.tagSlugs().size())).append("))");
            arguments.addAll(filters.tagSlugs());
        }
        if (filters.publishedAfter() != null) {
            sql.append(" and a.published_at >= ?");
            arguments.add(Timestamp.from(filters.publishedAfter()));
        }
        if (filters.publishedBefore() != null) {
            sql.append(" and a.published_at <= ?");
            arguments.add(Timestamp.from(filters.publishedBefore()));
        }
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

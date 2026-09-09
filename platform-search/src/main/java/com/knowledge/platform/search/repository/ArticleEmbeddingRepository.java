package com.knowledge.platform.search.repository;

import com.knowledge.platform.search.model.dto.RetrievalCandidate;
import com.knowledge.platform.search.model.dto.SearchFilters;
import com.knowledge.platform.search.model.entity.ArticleEmbedding;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The semantic index, accessed through JDBC because pgvector's type and operators have no JPA
 * equivalent.
 */
@Repository
public class ArticleEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ArticleSearchDocumentRepository filterSupport;

    public ArticleEmbeddingRepository(
            JdbcTemplate jdbcTemplate, ArticleSearchDocumentRepository filterSupport) {
        this.jdbcTemplate = jdbcTemplate;
        // The metadata predicates are identical for lexical and vector retrieval; sharing the one
        // implementation is what keeps a filter fix from being applied to only one of them.
        this.filterSupport = filterSupport;
    }

    public void save(ArticleEmbedding embedding) {
        jdbcTemplate.update("""
                        insert into search.article_embeddings
                            (article_id, model, dimensions, embedding, content_hash, created_at, updated_at)
                        values (?, ?, ?, cast(? as vector), ?, now(), now())
                        on conflict (article_id) do update set
                            model        = excluded.model,
                            dimensions   = excluded.dimensions,
                            embedding    = excluded.embedding,
                            content_hash = excluded.content_hash,
                            updated_at   = now()
                        """,
                embedding.articleId(), embedding.model(), embedding.dimensions(),
                toVectorLiteral(embedding.embedding()), embedding.contentHash());
    }

    public void delete(UUID articleId) {
        jdbcTemplate.update("delete from search.article_embeddings where article_id = ?", articleId);
    }

    /**
     * Published articles whose current content has no matching vector.
     *
     * <p>The join condition compares content hashes, not just article ids, so this finds both articles
     * that were never embedded and articles edited since they were -- a stale vector is as useless for
     * retrieval as a missing one.
     */
    public List<ArticleBacklogEntry> findMissingEmbeddings(int limit) {
        return jdbcTemplate.query("""
                select a.id, a.content_hash
                  from content.articles a
                  left join search.article_embeddings e
                    on e.article_id = a.id and e.content_hash = a.content_hash
                 where a.publication_state = 'PUBLISHED'
                   and e.article_id is null
                 order by a.published_at desc
                 limit ?
                """,
                (rs, rowNum) -> new ArticleBacklogEntry(
                        rs.getObject("id", UUID.class), rs.getString("content_hash")),
                limit);
    }

    /** An article awaiting an embedding, and the content hash the vector must correspond to. */
    public record ArticleBacklogEntry(UUID articleId, String contentHash) {
    }

    public boolean isEmbeddedAt(UUID articleId, String contentHash) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from search.article_embeddings
                 where article_id = ? and content_hash = ?
                """, Integer.class, articleId, contentHash);
        return count != null && count > 0;
    }

    /**
     * Nearest-neighbour retrieval by cosine similarity.
     *
     * <p>{@code <=>} is pgvector's cosine <em>distance</em>, so similarity is {@code 1 - distance} and
     * lands in [0, 1] for normalized vectors -- the same range the lexical score is normalized into,
     * which is what makes the ranker's weights meaningful rather than arbitrary.
     *
     * <p>Ordering by the raw distance operator, not by the computed similarity alias, is what lets
     * PostgreSQL use the HNSW index. Ordering by {@code 1 - (embedding <=> ?)} descending would be
     * logically identical and would silently fall back to a sequential scan over every vector.
     */
    public List<RetrievalCandidate> searchSimilar(float[] queryVector, SearchFilters filters, int limit) {
        StringBuilder sql = new StringBuilder("""
                select e.article_id,
                       1 - (e.embedding <=> cast(? as vector)) as similarity
                  from search.article_embeddings e
                  join content.articles a on a.id = e.article_id
                 where a.publication_state = 'PUBLISHED'
                """);

        List<Object> arguments = new ArrayList<>();
        arguments.add(toVectorLiteral(queryVector));
        filterSupport.appendFilters(sql, arguments, filters);
        sql.append(" order by e.embedding <=> cast(? as vector) limit ?");
        arguments.add(toVectorLiteral(queryVector));
        arguments.add(limit);

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new RetrievalCandidate(
                        rs.getObject("article_id", UUID.class),
                        rs.getDouble("similarity"),
                        RetrievalCandidate.Source.VECTOR),
                arguments.toArray());
    }

    /** pgvector accepts a vector as the text form {@code [0.1,0.2,...]}. */
    private String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}

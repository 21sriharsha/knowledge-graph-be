package com.knowledge.platform.search.repository;

import com.knowledge.platform.search.ranking.ResultRanker;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Fetches, in one query, every fact ranking and result rendering need.
 *
 * <p>A single aggregating query rather than loading entities per candidate. A hundred candidates
 * through JPA would be a hundred article loads plus their tags, topics and authors -- the exact N+1
 * the read-model architecture exists to avoid, reintroduced on the hottest path in the application.
 */
@Repository
public class ArticleFactsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArticleFactsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<UUID, ResultRanker.ArticleFacts> findFacts(Collection<UUID> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(articleIds.size(), "?"));

        // array_remove(..., null) because the left joins produce a NULL element for an article with
        // no tags or no topics, which would otherwise arrive as a single-element list containing null.
        String sql = """
                select a.id,
                       a.slug,
                       a.title,
                       a.summary,
                       a.author_id,
                       au.slug         as author_slug,
                       au.display_name as author_name,
                       a.published_at,
                       a.reading_time_minutes,
                       array_remove(array_agg(distinct tp.slug), null) as topic_slugs,
                       array_remove(array_agg(distinct tg.slug), null) as tag_slugs
                  from content.articles a
                  join author.authors au on au.id = a.author_id
                  left join content.article_topics at on at.article_id = a.id
                  left join content.topics tp on tp.id = at.topic_id
                  left join content.article_tags at2 on at2.article_id = a.id
                  left join content.tags tg on tg.id = at2.tag_id
                 where a.id in (%s)
                   and a.publication_state = 'PUBLISHED'
                 group by a.id, au.slug, au.display_name
                """.formatted(placeholders);

        List<ResultRanker.ArticleFacts> facts = jdbcTemplate.query(sql, (rs, rowNum) ->
                        new ResultRanker.ArticleFacts(
                                rs.getObject("id", UUID.class),
                                rs.getString("slug"),
                                rs.getString("title"),
                                rs.getString("summary"),
                                rs.getObject("author_id", UUID.class),
                                rs.getString("author_slug"),
                                rs.getString("author_name"),
                                toList(rs.getArray("topic_slugs")),
                                toList(rs.getArray("tag_slugs")),
                                rs.getTimestamp("published_at") == null
                                        ? null : rs.getTimestamp("published_at").toInstant(),
                                rs.getInt("reading_time_minutes")),
                articleIds.toArray());

        return facts.stream().collect(
                Collectors.toMap(ResultRanker.ArticleFacts::articleId, Function.identity()));
    }

    private List<String> toList(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return values == null ? List.of() : List.of(values);
    }
}

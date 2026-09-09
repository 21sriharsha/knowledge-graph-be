package com.knowledge.platform.delivery.repository;

import com.knowledge.platform.delivery.model.dto.TrendingArticle;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Readership counts, accessed through JDBC.
 *
 * <p>JDBC rather than JPA for the same reason the search module uses it: the write is an upsert with
 * an arithmetic {@code DO UPDATE}, which is what makes concurrent flushes from several instances
 * correct without any coordination between them. Expressed through an entity it would become
 * read-modify-write, and two instances flushing at once would lose one of the two counts.
 */
@Repository
public class ArticleViewRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArticleViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Adds a batch of counts to today's buckets.
     *
     * <p>Counts are <em>added</em>, never assigned, so this is safe to run concurrently with itself
     * on any number of instances and safe to retry: a repeated flush of the same batch overcounts,
     * which is why the caller drains its buffer before calling rather than after.
     *
     * <p>An article deleted between the read and the flush is skipped rather than failing the batch;
     * losing a view for an article that no longer exists is not worth an error.
     */
    public void addViews(Map<UUID, Long> counts, LocalDate day) {
        if (counts.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, Long>> batch = List.copyOf(counts.entrySet());
        jdbcTemplate.batchUpdate("""
                        insert into analytics.article_view_counts (article_id, view_date, views)
                        select ?, ?, ?
                         where exists (select 1 from content.articles where id = ?)
                        on conflict (article_id, view_date) do update
                            set views = analytics.article_view_counts.views + excluded.views
                        """,
                batch,
                batch.size(),
                (ps, entry) -> {
                    ps.setObject(1, entry.getKey());
                    ps.setObject(2, day);
                    ps.setLong(3, entry.getValue());
                    ps.setObject(4, entry.getKey());
                });
    }

    /**
     * The most-read published articles over a window ending today.
     *
     * <p>Ordered by views, then by recency, so that two articles on equal counts resolve to a stable
     * order rather than whatever the planner returns. Articles with no views in the window do not
     * appear at all -- padding the list with unread articles would make "trending" mean nothing.
     */
    public List<TrendingArticle> findTrending(LocalDate since, int limit) {
        return jdbcTemplate.query("""
                        select a.slug,
                               a.title,
                               au.display_name as author_name,
                               t.name          as topic,
                               sum(v.views)    as views
                          from analytics.article_view_counts v
                          join content.articles a on a.id = v.article_id
                          join author.authors au on au.id = a.author_id
                          left join content.topics t on t.id = a.primary_topic_id
                         where v.view_date >= ?
                           and a.publication_state = 'PUBLISHED'
                         group by a.id, a.slug, a.title, au.display_name, t.name, a.published_at
                        having sum(v.views) > 0
                         order by views desc, a.published_at desc nulls last
                         limit ?
                        """,
                (rs, rowNum) -> new TrendingArticle(
                        rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("author_name"),
                        rs.getString("topic"),
                        rs.getLong("views")),
                since, limit);
    }

    /** Total recorded views for one article, across all time. Used by tests and diagnostics. */
    public long totalViews(UUID articleId) {
        Long total = jdbcTemplate.queryForObject(
                "select coalesce(sum(views), 0) from analytics.article_view_counts where article_id = ?",
                Long.class, articleId);
        return total == null ? 0 : total;
    }
}

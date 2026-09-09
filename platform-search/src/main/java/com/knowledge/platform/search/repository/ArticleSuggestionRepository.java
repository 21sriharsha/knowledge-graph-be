package com.knowledge.platform.search.repository;

import com.knowledge.platform.search.model.dto.SearchSuggestion;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Prefix and substring matching for the search box's typeahead.
 *
 * <p>Separate from the lexical index on purpose. {@code search_vector} is built with
 * {@code to_tsvector}, which stores whole stemmed lexemes, so {@code websearch_to_tsquery('kub')}
 * matches nothing at all -- a full-text index is blind to a word the reader has not finished
 * typing, which is the entire span of time a typeahead is useful. Suggestions are therefore a
 * different retrieval problem and get their own query.
 *
 * <p>{@code content.articles} already carries a trigram index on {@code title}, created for exactly
 * this kind of prefix/fuzzy path, so the {@code ILIKE} below is indexed rather than a scan.
 */
@Repository
public class ArticleSuggestionRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArticleSuggestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Published articles whose title or author name contains {@code term}.
     *
     * <p>Ordered by how the match was made rather than by any relevance score: a title that
     * <em>starts</em> with what you typed is almost always the one you meant, a title that merely
     * contains it is next, and an article surfacing because its author matched is last. Within a
     * tier the shorter title wins, because a short title containing the term is more specifically
     * about it.
     */
    public List<SearchSuggestion> findMatching(String term, int limit) {
        String pattern = "%" + escapeLikeWildcards(term) + "%";
        String prefix = escapeLikeWildcards(term) + "%";

        return jdbcTemplate.query("""
                        select a.slug,
                               a.title,
                               au.display_name as author_name,
                               t.name          as topic,
                               case when a.title ilike ? escape '\\' then 0
                                    when a.title ilike ? escape '\\' then 1
                                    else 2
                               end as tier
                          from content.articles a
                          join author.authors au on au.id = a.author_id
                          left join content.topics t on t.id = a.primary_topic_id
                         where a.publication_state = 'PUBLISHED'
                           and (a.title ilike ? escape '\\'
                                or au.display_name ilike ? escape '\\')
                         order by tier, length(a.title), a.published_at desc nulls last
                         limit ?
                        """,
                (rs, rowNum) -> new SearchSuggestion(
                        rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("author_name"),
                        rs.getString("topic")),
                prefix, pattern, pattern, pattern, limit);
    }

    /**
     * Neutralises LIKE metacharacters in reader input.
     *
     * <p>Not an injection concern -- every value here is a bind parameter. It is a correctness one:
     * a reader typing {@code 100%} or {@code _} would otherwise be handing the pattern matcher a
     * wildcard and getting results that have nothing to do with what they typed.
     */
    private String escapeLikeWildcards(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.TrendingArticle;
import com.knowledge.platform.delivery.repository.ArticleViewRepository;
import com.knowledge.platform.delivery.service.ArticleViewService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Readership counting, end to end against a real database.
 *
 * <p>The behaviour that matters here is arithmetic under concurrency and time: counts must add
 * rather than overwrite, the window must actually exclude what is outside it, and an unread platform
 * must say so instead of inventing an order.
 */
class TrendingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ArticleViewService articleViews;

    @Autowired
    private ArticleViewRepository viewRepository;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    private Article popular;
    private Article quiet;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("""
                truncate table analytics.article_view_counts, read_model.article_read_models,
                               read_model.routes, search.article_embeddings,
                               search.article_search_documents, graph.suggested_relationships,
                               graph.article_links, content.article_topics, content.article_tags,
                               content.article_revisions, content.articles, content.topics,
                               content.tags, ingestion.events, ingestion.runs,
                               source.webhook_deliveries, source.repositories, author.authors
                restart identity cascade
                """);
        // The trending cache is not invalidated by anything -- by design, since no event marks
        // "trending changed" -- so truncating the tables behind it is not enough to isolate a test.
        cacheManager.getCache("trendingArticles").clear();

        Author author = authorService.findOrCreateByName("Harsh", "harsh@example.com");
        popular = publish("Widely Read", author);
        quiet = publish("Barely Read", author);
    }

    private Article publish(String title, Author author) {
        return articleService.upsert(new ArticleUpsertCommand(
                null, title, "Body of " + title, "# " + title, "hash-" + title.hashCode(),
                author.getId(), List.of(), List.of(), 3, 1, true, null)).article();
    }

    @Test
    @DisplayName("nothing is trending until something has been read")
    void reportsNothingBeforeAnythingIsRead() {
        assertThat(articleViews.trending(7, 6)).isEmpty();
    }

    @Test
    @DisplayName("recorded views accumulate and order the list")
    void ranksByRecordedViews() {
        record(popular, 5);
        record(quiet, 1);
        articleViews.flush();

        assertThat(articleViews.trending(7, 6))
                .extracting(TrendingArticle::title, TrendingArticle::views)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Widely Read", 5L),
                        org.assertj.core.groups.Tuple.tuple("Barely Read", 1L));
    }

    @Test
    @DisplayName("a second flush adds to the first rather than replacing it")
    void accumulatesAcrossFlushes() {
        record(popular, 2);
        articleViews.flush();
        record(popular, 3);
        articleViews.flush();

        // The property that lets several instances flush into the same row without coordination.
        assertThat(viewRepository.totalViews(popular.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("views outside the window do not count towards trending")
    void honoursTheWindow() {
        viewRepository.addViews(Map.of(popular.getId(), 99L), LocalDate.now().minusDays(30));
        viewRepository.addViews(Map.of(quiet.getId(), 1L), LocalDate.now());

        // Ranked over a week, thirty-day-old attention is not what "trending" means.
        assertThat(articleViews.trending(7, 6))
                .extracting(TrendingArticle::title)
                .containsExactly("Barely Read");

        assertThat(articleViews.trending(365, 6))
                .extracting(TrendingArticle::title)
                .containsExactly("Widely Read", "Barely Read");
    }

    @Test
    @DisplayName("unpublishing removes an article from trending")
    void excludesUnpublishedArticles() {
        record(popular, 10);
        articleViews.flush();
        articleService.unpublish(new Slug(popular.getSlug()));

        assertThat(articleViews.trending(7, 6)).isEmpty();
    }

    @Test
    @DisplayName("a view of an unknown article is ignored rather than failing")
    void ignoresViewsOfArticlesThatDoNotExist() {
        articleViews.recordView(Slug.of("no-such-article"));
        articleViews.flush();

        assertThat(articleViews.trending(7, 6)).isEmpty();
    }

    private void record(Article article, int times) {
        for (int i = 0; i < times; i++) {
            articleViews.recordView(new Slug(article.getSlug()));
        }
    }
}

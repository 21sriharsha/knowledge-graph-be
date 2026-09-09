package com.knowledge.platform.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.Topic;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * The rule this class exists to honour is that publishing one article must not throw away every
 * cached read model. These tests pin what is evicted and, just as importantly, what is left alone.
 */
class ReadModelCacheInvalidatorTest {

    private CacheManager cacheManager;
    private ReadModelCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(
                "articleReadModels", "authorReadModels", "topicReadModels",
                "graphNeighbourhoods", "routeResolutions", "navigation");
        invalidator = new DefaultReadModelCacheInvalidatorImpl(cacheManager);
    }

    @Test
    @DisplayName("an unrelated article's cached read model survives a publish")
    void doesNotClearUnrelatedArticles() {
        put("articleReadModels", "kubernetes-networking", "cached");
        put("articleReadModels", "unrelated-article", "cached");

        invalidator.afterArticleChanged(article("kubernetes-networking"), "harsh", Set.of());

        assertThat(get("articleReadModels", "kubernetes-networking")).isNull();
        // The whole point: a site with thousands of articles must not re-assemble all of them
        // because one changed.
        assertThat(get("articleReadModels", "unrelated-article")).isEqualTo("cached");
    }

    @Test
    @DisplayName("articles that link to the changed one are evicted: their backlinks changed too")
    void evictsLinkingArticles() {
        put("articleReadModels", "linking-article", "cached");
        put("articleReadModels", "unrelated-article", "cached");

        invalidator.afterArticleChanged(
                article("kubernetes-networking"), "harsh", Set.of("linking-article"));

        assertThat(get("articleReadModels", "linking-article")).isNull();
        assertThat(get("articleReadModels", "unrelated-article")).isEqualTo("cached");
    }

    @Test
    void evictsTheAuthorAndTopicPagesWhoseListingsChanged() {
        put("authorReadModels", "harsh", "cached");
        put("authorReadModels", "someone-else", "cached");
        put("topicReadModels", "kubernetes", "cached");
        put("topicReadModels", "unrelated-topic", "cached");

        Article article = article("kubernetes-networking");
        article.replaceTopics(Set.of(Topic.create("kubernetes", "Kubernetes")));

        invalidator.afterArticleChanged(article, "harsh", Set.of());

        assertThat(get("authorReadModels", "harsh")).isNull();
        assertThat(get("authorReadModels", "someone-else")).isEqualTo("cached");
        assertThat(get("topicReadModels", "kubernetes")).isNull();
        assertThat(get("topicReadModels", "unrelated-topic")).isEqualTo("cached");
    }

    @Test
    @DisplayName("graph and route caches are swept, because their keys do not name the article")
    void sweepsCachesWhoseKeysCannotBeDerived() {
        // A neighbourhood's key is (slug, depth, suggestions), so the entries containing a given
        // article are not derivable from its id. Sweeping a bounded, cheap cache beats maintaining a
        // reverse index whose staleness would be a subtler bug.
        put("graphNeighbourhoods", "other:1:true", "cached");
        put("routeResolutions", "/articles/other", "cached");
        put("navigation", "site", "cached");

        invalidator.afterArticleChanged(article("kubernetes-networking"), "harsh", Set.of());

        assertThat(get("graphNeighbourhoods", "other:1:true")).isNull();
        assertThat(get("routeResolutions", "/articles/other")).isNull();
        assertThat(get("navigation", "site")).isNull();
    }

    @Test
    @DisplayName("a profile edit touches the author's page and navigation, not every article")
    void authorChangeIsNarrow() {
        put("articleReadModels", "some-article", "cached");
        put("authorReadModels", "harsh", "cached");
        put("navigation", "site", "cached");

        invalidator.afterAuthorChanged("harsh");

        assertThat(get("articleReadModels", "some-article")).isEqualTo("cached");
        assertThat(get("authorReadModels", "harsh")).isNull();
        assertThat(get("navigation", "site")).isNull();
    }

    @Test
    void toleratesAMissingAuthorSlug() {
        invalidator.afterArticleChanged(article("kubernetes-networking"), null, Set.of());
    }

    private Article article(String slug) {
        return Article.create(slug, "Title", "Summary", "# Title", "hash", UUID.randomUUID(), 100, 1);
    }

    private void put(String cacheName, Object key, Object value) {
        cache(cacheName).put(key, value);
    }

    private Object get(String cacheName, Object key) {
        Cache.ValueWrapper wrapper = cache(cacheName).get(key);
        return wrapper == null ? null : wrapper.get();
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }
}

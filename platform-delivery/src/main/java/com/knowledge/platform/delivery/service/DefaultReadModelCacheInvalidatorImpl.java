package com.knowledge.platform.delivery.service;

import com.knowledge.platform.content.model.entity.Article;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Default ReadModelCacheInvalidator.
 *
 * <p>See {@link ReadModelCacheInvalidator} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultReadModelCacheInvalidatorImpl implements ReadModelCacheInvalidator {

    private static final String ARTICLE_READ_MODELS = "articleReadModels";
    private static final String AUTHOR_READ_MODELS = "authorReadModels";
    private static final String TOPIC_READ_MODELS = "topicReadModels";
    private static final String TAG_READ_MODELS = "tagReadModels";
    private static final String TAXONOMY_LISTINGS = "taxonomyListings";
    private static final String GRAPH_NEIGHBOURHOODS = "graphNeighbourhoods";
    private static final String ROUTE_RESOLUTIONS = "routeResolutions";
    private static final String NAVIGATION = "navigation";

    private final CacheManager cacheManager;

    public DefaultReadModelCacheInvalidatorImpl(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void afterArticleChanged(
            Article article, String authorSlug, Set<String> linkingArticleSlugs) {
        evict(ARTICLE_READ_MODELS, article.getSlug());
        linkingArticleSlugs.forEach(slug -> evict(ARTICLE_READ_MODELS, slug));

        if (authorSlug != null) {
            evict(AUTHOR_READ_MODELS, authorSlug);
        }
        article.getTopics().forEach(topic -> evict(TOPIC_READ_MODELS, topic.getSlug()));
        article.getTags().forEach(tag -> evict(TAG_READ_MODELS, tag.getSlug()));

        // The browse indexes carry per-term article counts, so publishing anything changes them.
        // Two entries, so clearing is cheaper than reasoning about which term moved.
        evictAll(TAXONOMY_LISTINGS);

        evictAll(GRAPH_NEIGHBOURHOODS);
        evictAll(ROUTE_RESOLUTIONS);
        evictAll(NAVIGATION);

        log.debug("Invalidated caches for '{}' and {} linking article(s)",
                article.getSlug(), linkingArticleSlugs.size());
    }

    @Override
    public void afterAuthorChanged(String authorSlug) {
        evict(AUTHOR_READ_MODELS, authorSlug);
        evictAll(NAVIGATION);
    }

    @Override
    public void afterArticleSlugChanged(String slug) {
        evict(ARTICLE_READ_MODELS, slug);
        evictAll(GRAPH_NEIGHBOURHOODS);
        evictAll(ROUTE_RESOLUTIONS);
        evictAll(NAVIGATION);
        evictAll(TAXONOMY_LISTINGS);
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}

package com.knowledge.platform.app.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine caches for the read path.
 *
 * <p>The cache is an optimization and never a source of truth: every entry is derivable from
 * PostgreSQL, and the application must remain correct with it empty.
 *
 * <p>Caches are declared explicitly rather than created on demand, so a typo in a {@code @Cacheable}
 * name fails fast instead of silently creating an unbounded, unmonitored cache that nobody
 * invalidates.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(CacheProperties properties, MeterRegistry meterRegistry) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                caffeineCache(CacheNames.ARTICLE_READ_MODELS, properties.readModelMaximumSize(),
                        properties.readModelTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.AUTHOR_READ_MODELS, properties.readModelMaximumSize(),
                        properties.readModelTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.TOPIC_READ_MODELS, properties.readModelMaximumSize(),
                        properties.readModelTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.TAG_READ_MODELS, properties.readModelMaximumSize(),
                        properties.readModelTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.TAXONOMY_LISTINGS, 8,
                        properties.readModelTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.GRAPH_NEIGHBOURHOODS, properties.graphMaximumSize(),
                        properties.graphTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.ROUTE_RESOLUTIONS, properties.routeMaximumSize(),
                        properties.routeTimeToLive(), meterRegistry),
                caffeineCache(CacheNames.NAVIGATION, 16,
                        properties.readModelTimeToLive(), meterRegistry),
                // Short-lived on purpose: this one is never invalidated. View counts change
                // continuously and no event marks "trending changed", so freshness here is a
                // question of how stale the list may get, not of catching an update.
                caffeineCache(CacheNames.TRENDING_ARTICLES, 32,
                        properties.trendingTimeToLive(), meterRegistry),
                // Bounded by entry count, not bytes, which is the wrong unit for images -- 200
                // large ones would be a lot of heap. Kept small for that reason; the real caching
                // happens in the browser, which is what the response's cache headers are for.
                caffeineCache(CacheNames.REPOSITORY_ASSETS, 200,
                        properties.trendingTimeToLive(), meterRegistry)));
        manager.initializeCaches();
        return manager;
    }

    private CaffeineCache caffeineCache(
            String name, long maximumSize, Duration timeToLive, MeterRegistry meterRegistry) {
        Cache<Object, Object> cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(timeToLive)
                .recordStats()
                .build();
        // Hit/miss visibility is a stated observability requirement, so it is wired here rather than
        // left to whoever remembers to enable statistics at a call site.
        CaffeineCacheMetrics.monitor(meterRegistry, cache, name);
        return new CaffeineCache(name, cache);
    }
}

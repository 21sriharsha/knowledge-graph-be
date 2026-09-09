package com.knowledge.platform.app.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sizing for the Caffeine caches.
 *
 * <p>Every cache is bounded by both entry count and time to live. The TTL is a safety net rather than
 * the correctness mechanism: ingestion performs targeted invalidation, and the TTL only limits how
 * long an entry could remain stale if an invalidation were ever missed.
 */
@ConfigurationProperties(prefix = "knowledge.cache")
public record CacheProperties(
        @Min(1) long readModelMaximumSize,
        Duration readModelTimeToLive,
        @Min(1) long graphMaximumSize,
        Duration graphTimeToLive,
        @Min(1) long routeMaximumSize,
        Duration routeTimeToLive,
        Duration trendingTimeToLive) {

    public CacheProperties {
        readModelTimeToLive = readModelTimeToLive == null ? Duration.ofHours(1) : readModelTimeToLive;
        graphTimeToLive = graphTimeToLive == null ? Duration.ofMinutes(30) : graphTimeToLive;
        routeTimeToLive = routeTimeToLive == null ? Duration.ofHours(6) : routeTimeToLive;
        trendingTimeToLive =
                trendingTimeToLive == null ? Duration.ofMinutes(5) : trendingTimeToLive;
    }
}

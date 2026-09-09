package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.delivery.service.ReadModelCacheInvalidator;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Invalidates the caches this article's change made stale.
 *
 * <p>Last in the chain, deliberately. Invalidating earlier would let a request repopulate a cache
 * from half-materialized state -- a read model built before the graph handler ran, say -- and that
 * entry would then be wrong until its TTL expired, with nothing to indicate why.
 *
 * <p>Targeted, not wholesale: the article, the articles that link to it, its author and its topics.
 * Clearing every cached read model because one article changed would turn a routine publish into a
 * re-assembly stampede across the whole site.
 */
@Component
@Order(HandlerOrder.CACHE_INVALIDATION)
public class CacheInvalidationHandler implements IngestionHandler {

    private final ReadModelCacheInvalidator cacheInvalidator;

    public CacheInvalidationHandler(ReadModelCacheInvalidator cacheInvalidator) {
        this.cacheInvalidator = cacheInvalidator;
    }

    @Override
    public String name() {
        return "invalidate-cache";
    }

    @Override
    public void handle(IngestionContext context) {
        cacheInvalidator.afterArticleChanged(
                context.requireArticle(),
                context.requireAuthor().getSlug(),
                context.invalidatedArticleSlugs());
    }
}

package com.knowledge.platform.app.config;

/**
 * The cache names used across the application.
 *
 * <p>Declared with the cache configuration rather than inside one module, because invalidation is
 * inherently cross-module: ingestion changes canonical content, and the delivery, graph and route
 * caches derived from it all have to be told. Naming them in one place is what keeps the
 * {@code @Cacheable} annotations and the Caffeine specifications from drifting apart.
 */
public final class CacheNames {

    public static final String ARTICLE_READ_MODELS = "articleReadModels";
    public static final String AUTHOR_READ_MODELS = "authorReadModels";
    public static final String TOPIC_READ_MODELS = "topicReadModels";
    public static final String TAG_READ_MODELS = "tagReadModels";

    /** The topic and tag browse indexes, keyed by which of the two. */
    public static final String TAXONOMY_LISTINGS = "taxonomyListings";
    public static final String GRAPH_NEIGHBOURHOODS = "graphNeighbourhoods";
    public static final String ROUTE_RESOLUTIONS = "routeResolutions";
    public static final String NAVIGATION = "navigation";

    private CacheNames() {
        throw new AssertionError("constants holder");
    }
}

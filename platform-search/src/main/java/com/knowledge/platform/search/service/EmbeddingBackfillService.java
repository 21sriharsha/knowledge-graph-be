package com.knowledge.platform.search.service;

/**
 * Fills in embeddings that ingestion could not produce at the time.
 *
 * <p>The reliability rule is that an article stays publishable when embedding generation fails. That
 * makes a backlog inevitable, and this is what drains it. Three ways an article ends up here:
 *
 * <ul>
 *   <li>it was ingested while {@code knowledge.ai.enabled} was false;
 *   <li>the embedding provider was unreachable and the job exhausted its retries;
 *   <li>the article was edited, so its stored vector no longer matches its content.
 * </ul>
 *
 * <p>Without this, an outage during ingestion would cost those articles their semantic
 * discoverability permanently -- nothing else would ever revisit them, because ingestion only looks
 * at content that has changed.
 */
public interface EmbeddingBackfillService {

    /**
     * Schedules embedding jobs for articles that are missing a current vector.
     *
     * @return how many were scheduled; zero when the provider is unavailable or nothing is outstanding
     */
    int backfill();

    /** How many published articles currently lack a vector for their content. */
    long backlogSize();
}

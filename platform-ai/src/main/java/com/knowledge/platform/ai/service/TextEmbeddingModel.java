package com.knowledge.platform.ai.service;

import java.util.Optional;

/**
 * The application's abstraction over embedding generation.
 *
 * <p>Like query understanding, embedding is allowed to be unavailable: an article must remain
 * publishable when the embedding provider is down, with the vector generated later by a retried job.
 * Returning empty rather than throwing is what makes that the obvious implementation.
 *
 * <p>{@link #dimensions()} is part of the contract because the schema fixes the vector width. A
 * provider whose dimension does not match the column cannot be swapped in silently.
 */
public interface TextEmbeddingModel {

    /** @return the vector, or empty when embedding was unavailable */
    Optional<float[]> embed(String text);

    /** Vector width this model produces. Must match the {@code VECTOR(n)} column. */
    int dimensions();

    /** Provider-specific model identifier, stored alongside the vector for staleness detection. */
    String modelName();

    /** Whether the provider is currently usable, for health reporting and job scheduling. */
    boolean isAvailable();
}

package com.knowledge.platform.ai.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AI boundary.
 *
 * @param enabled master switch. When false the heuristic analyzer handles every query and no vectors
 *     are generated, which is the intended configuration for tests and for any environment without a
 *     model runtime.
 * @param queryUnderstandingTimeout how long a query may wait on the SLM before the search falls back
 *     to deterministic retrieval. Bounded low on purpose: a reader waiting on a slow model is worse
 *     served than one who gets lexical results immediately.
 * @param embeddingDimensions vector width the provider must produce, checked against what comes back
 * @param maxQueryLength queries longer than this are not sent to the model at all; a very long input
 *     is either a paste accident or an attempt to manipulate the prompt
 */
@ConfigurationProperties(prefix = "knowledge.ai")
public record AiProperties(
        boolean enabled,
        Duration queryUnderstandingTimeout,
        int embeddingDimensions,
        int maxQueryLength) {

    public AiProperties {
        queryUnderstandingTimeout =
                queryUnderstandingTimeout == null ? Duration.ofSeconds(3) : queryUnderstandingTimeout;
        embeddingDimensions = embeddingDimensions <= 0 ? 768 : embeddingDimensions;
        maxQueryLength = maxQueryLength <= 0 ? 500 : maxQueryLength;
    }
}

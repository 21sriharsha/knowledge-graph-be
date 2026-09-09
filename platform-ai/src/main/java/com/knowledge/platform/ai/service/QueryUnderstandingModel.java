package com.knowledge.platform.ai.service;

import com.knowledge.platform.ai.model.dto.SearchIntent;
import java.util.Optional;

/**
 * The application's abstraction over query understanding.
 *
 * <p>The search module depends on this interface and on {@link SearchIntent}. It knows nothing about
 * Ollama, Spring AI, prompts or tokens, so the model provider is replaceable without search changing:
 * a local SLM today, a hosted model later, a deterministic stub in tests.
 *
 * <p><strong>Why the return type is {@code Optional}.</strong> The SLM is an optimization, not a
 * dependency. A model that is disabled, unreachable, slow, or producing output that fails validation
 * must degrade search to deterministic retrieval rather than fail the request. Making that an empty
 * return rather than an exception puts the degradation in the type, so a caller cannot forget it.
 */
public interface QueryUnderstandingModel {

    /**
     * Interprets a natural-language query.
     *
     * @return the validated intent, or empty when understanding was unavailable or untrustworthy
     */
    Optional<SearchIntent> understand(String query);
}

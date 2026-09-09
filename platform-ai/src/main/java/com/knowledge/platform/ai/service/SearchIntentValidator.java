package com.knowledge.platform.ai.service;

import com.knowledge.platform.ai.model.dto.SearchIntent;
import java.util.Optional;

/**
 * Schema-validates and normalizes model output before it can influence retrieval.
 *
 * <p>Model output is untrusted input. Not because the model is adversarial, but because it is
 * probabilistic: it invents enum values, returns forty topics for a four-word query, echoes the
 * entire prompt into {@code queryText}, and produces date ranges that end before they begin. Every
 * one of those becomes a bad query plan, a slow query, or an empty result page.
 *
 * <p>This validator is therefore a bound, not a check. It repairs what it can and rejects what it
 * cannot, so the planner only ever sees an intent that is safe to act on.
 */
public interface SearchIntentValidator {

    /**
     * Validates raw model output against the original query.
     *
     * @param raw what the model produced, possibly null or partially malformed
     * @param originalQuery the reader's query, used as the fallback for an unusable {@code queryText}
     * @return a safe intent, or empty when the output was too damaged to repair
     */
    Optional<SearchIntent> validate(SearchIntent raw, String originalQuery);
}

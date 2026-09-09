package com.knowledge.platform.ai.service;

import com.knowledge.platform.ai.model.dto.DateRange;
import com.knowledge.platform.ai.model.dto.QueryIntentType;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.model.dto.SearchMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default SearchIntentValidator.
 *
 * <p>See {@link SearchIntentValidator} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultSearchIntentValidatorImpl implements SearchIntentValidator {

    /**
     * A real query names a handful of subjects. Anything beyond this is the model padding, and each
     * extra term is another OR branch the database has to evaluate.
     */
    private static final int MAX_FACETS = 8;

    /** Longer than any genuine topic, tag or author name. */
    private static final int MAX_FACET_LENGTH = 120;

    private static final int MAX_QUERY_TEXT_LENGTH = 500;

    @Override
    public Optional<SearchIntent> validate(SearchIntent raw, String originalQuery) {
        if (raw == null) {
            return Optional.empty();
        }

        String queryText = normalizeQueryText(raw.queryText(), originalQuery);
        if (queryText == null) {
            log.debug("Rejecting intent: no usable query text for query '{}'", originalQuery);
            return Optional.empty();
        }

        return Optional.of(new SearchIntent(
                raw.intent() == null ? QueryIntentType.ARTICLE_SEARCH : raw.intent(),
                normalizeFacet(raw.author()),
                normalizeFacets(raw.topics()),
                normalizeFacets(raw.tags()),
                queryText,
                normalizeDateRange(raw.dateRange()),
                raw.mode() == null ? SearchMode.HYBRID : raw.mode()));
    }

    /**
     * The model is asked to strip filter phrasing from {@code queryText}, but frequently returns the
     * whole query, an empty string, or a restatement longer than the input. Falling back to the
     * original query is always safe: matching too much text costs relevance, matching none costs the
     * reader their results.
     */
    private String normalizeQueryText(String modelText, String originalQuery) {
        String candidate = trimToNull(modelText);
        if (candidate == null) {
            candidate = trimToNull(originalQuery);
        }
        if (candidate == null) {
            return null;
        }
        return candidate.length() > MAX_QUERY_TEXT_LENGTH
                ? candidate.substring(0, MAX_QUERY_TEXT_LENGTH)
                : candidate;
    }

    private List<String> normalizeFacets(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        // LinkedHashSet: models repeat themselves ("Kubernetes", "kubernetes"), and duplicate facets
        // would double-count in ranking. Insertion order is kept because a model's first answers are
        // generally its most confident ones, and truncation should drop the least confident.
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeFacet(value);
            if (normalized != null) {
                distinct.add(normalized.toLowerCase(Locale.ROOT));
            }
            if (distinct.size() >= MAX_FACETS) {
                break;
            }
        }
        return List.copyOf(distinct);
    }

    private String normalizeFacet(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() > MAX_FACET_LENGTH) {
            return null;
        }
        return trimmed;
    }

    /** An inverted range would match nothing; treating it as unbounded matches the reader's intent. */
    private DateRange normalizeDateRange(DateRange range) {
        if (range == null || range.isUnbounded()) {
            return DateRange.unbounded();
        }
        if (range.isInverted()) {
            log.debug("Discarding inverted date range {} .. {}", range.from(), range.to());
            return DateRange.unbounded();
        }
        return range;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

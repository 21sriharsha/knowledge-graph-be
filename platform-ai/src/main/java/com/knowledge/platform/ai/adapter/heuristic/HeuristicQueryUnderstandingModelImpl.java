package com.knowledge.platform.ai.adapter.heuristic;

import com.knowledge.platform.ai.model.dto.DateRange;
import com.knowledge.platform.ai.model.dto.QueryIntentType;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.model.dto.SearchMode;
import com.knowledge.platform.ai.service.QueryUnderstandingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * A deterministic {@link com.knowledge.platform.ai.service.QueryUnderstandingModel} that uses no
 * model at all.
 *
 * <p>It serves three purposes, and each of them matters:
 *
 * <ul>
 *   <li>It is the <b>fallback</b> when the SLM is unreachable or its output fails validation, so
 *       search degrades in quality rather than failing.
 *   <li>It is the <b>only</b> implementation when AI is switched off, which is the intended
 *       configuration for CI and for any environment without a model runtime.
 *   <li>It makes AI-dependent behaviour <b>testable without a live model</b>, which the testing
 *       philosophy requires.
 * </ul>
 *
 * <p>It handles the explicit syntaxes a reader can type deliberately -- {@code by:harsh},
 * {@code tag:postgres}, {@code "exact phrase"} -- plus the one natural-language pattern common
 * enough to be worth recognising without a model, {@code ... by <Name>}. It does not attempt real
 * language understanding; that is precisely what the SLM is for.
 */
@Service(HeuristicQueryUnderstandingModelImpl.BEAN_NAME)
public class HeuristicQueryUnderstandingModelImpl
        implements QueryUnderstandingModel {

    /**
     * Bean name for the deterministic analyzer.
     *
     * <p>Named because callers need to ask for <em>this</em> implementation rather than the primary
     * one: the search path uses it directly whenever a query does not warrant an inference call, so
     * that explicit field syntax is still parsed.
     */
    public static final String BEAN_NAME = "deterministicQueryUnderstandingModel";

    /** Explicit field syntax: {@code by:harsh}, {@code author:harsh}. */
    private static final Pattern AUTHOR_FIELD =
            Pattern.compile("\\b(?:by|author)\\s*:\\s*(\"[^\"]+\"|\\S+)", Pattern.CASE_INSENSITIVE);

    /** Explicit field syntax: {@code tag:postgres}, and the {@code #postgres} shorthand. */
    private static final Pattern TAG_FIELD =
            Pattern.compile("\\btag\\s*:\\s*(\"[^\"]+\"|\\S+)|#([\\w-]+)", Pattern.CASE_INSENSITIVE);

    /** Explicit field syntax: {@code topic:kubernetes}. */
    private static final Pattern TOPIC_FIELD =
            Pattern.compile("\\btopic\\s*:\\s*(\"[^\"]+\"|\\S+)", Pattern.CASE_INSENSITIVE);

    /**
     * Trailing "written by Harsh" / "by Harsh". Anchored to the end and limited to capitalised words
     * so that it cannot swallow "sorted by relevance" or "indexed by postgres".
     */
    private static final Pattern TRAILING_AUTHOR = Pattern.compile(
            "\\b(?:written\\s+by|authored\\s+by|by)\\s+([A-Z][\\w.'-]*(?:\\s+[A-Z][\\w.'-]*){0,2})\\s*$");

    /** A quoted phrase means the reader wants those exact words, which is a lexical request. */
    private static final Pattern QUOTED_PHRASE = Pattern.compile("\"([^\"]+)\"");

    @Override
    public Optional<SearchIntent> understand(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String remaining = query;
        List<String> tags = new ArrayList<>();
        List<String> topics = new ArrayList<>();

        Extraction tagExtraction = extractAll(remaining, TAG_FIELD);
        tags.addAll(tagExtraction.values());
        remaining = tagExtraction.remainder();

        Extraction topicExtraction = extractAll(remaining, TOPIC_FIELD);
        topics.addAll(topicExtraction.values());
        remaining = topicExtraction.remainder();

        Extraction authorExtraction = extractAll(remaining, AUTHOR_FIELD);
        remaining = authorExtraction.remainder();
        String author = authorExtraction.values().isEmpty() ? null : authorExtraction.values().getFirst();

        if (author == null) {
            Matcher trailing = TRAILING_AUTHOR.matcher(remaining.trim());
            if (trailing.find()) {
                author = trailing.group(1).trim();
                remaining = remaining.substring(0, trailing.start()).trim();
            }
        }

        String queryText = collapseWhitespace(remaining);
        if (queryText.isEmpty()) {
            // The whole query was filter syntax -- "by:harsh tag:postgres" with no free text. That is
            // a browse request, not a text match, and the planner handles it through filters alone.
            queryText = collapseWhitespace(query);
        }

        return Optional.of(new SearchIntent(
                intentFor(author, topics, tags, remaining),
                author,
                List.copyOf(topics),
                List.copyOf(tags),
                queryText,
                DateRange.unbounded(),
                modeFor(query)));
    }

    /**
     * A quoted phrase is an explicit request for those exact words; sending it to vector search would
     * return things that mean something similar, which is the opposite of what was asked for.
     */
    private SearchMode modeFor(String query) {
        return QUOTED_PHRASE.matcher(query).find() ? SearchMode.LEXICAL : SearchMode.HYBRID;
    }

    private QueryIntentType intentFor(
            String author, List<String> topics, List<String> tags, String freeText) {
        boolean noFreeText = collapseWhitespace(freeText).isEmpty();
        if (author != null && noFreeText && topics.isEmpty() && tags.isEmpty()) {
            return QueryIntentType.AUTHOR_SEARCH;
        }
        if (noFreeText && (!topics.isEmpty() || !tags.isEmpty())) {
            return QueryIntentType.TOPIC_SEARCH;
        }
        return QueryIntentType.ARTICLE_SEARCH;
    }

    private Extraction extractAll(String input, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        StringBuilder remainder = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            String value = firstNonNullGroup(matcher);
            if (value != null) {
                values.add(unquote(value).toLowerCase(Locale.ROOT));
            }
            remainder.append(input, lastEnd, matcher.start());
            lastEnd = matcher.end();
        }
        remainder.append(input.substring(lastEnd));
        return new Extraction(values, remainder.toString());
    }

    private String firstNonNullGroup(Matcher matcher) {
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group);
            }
        }
        return null;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String collapseWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s{2,}", " ");
    }

    /** What a pattern pulled out, and what was left of the query afterwards. */
    private record Extraction(List<String> values, String remainder) {
    }
}

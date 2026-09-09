package com.knowledge.platform.search.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Default QueryAnalyzer.
 *
 * <p>See {@link QueryAnalyzer} for what this provides and why it exists.
 */
@Service
public class DefaultQueryAnalyzerImpl implements QueryAnalyzer {

    /** Below this many words there is no sentence structure for a model to interpret. */
    private static final int MINIMUM_WORDS_FOR_UNDERSTANDING = 4;

    /**
     * Words that signal a relationship worth extracting. Deliberately small: every entry is a word
     * whose presence genuinely changes how a query should be planned.
     */
    private static final Set<String> RELATIONAL_TERMS = Set.of(
            "by", "from", "about", "on", "written", "authored", "wrote", "related",
            "similar", "what", "which", "who", "how", "when", "explain", "difference",
            "between", "versus", "vs", "compare", "since", "before", "after", "recent", "latest");

    /** Explicit field syntax the deterministic analyzer already parses exactly. */
    private static final Pattern EXPLICIT_FIELD_SYNTAX =
            Pattern.compile("\\b(?:by|author|tag|topic)\\s*:", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public boolean warrantsQueryUnderstanding(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        // The reader has already said precisely what they mean; a model can only degrade that.
        if (EXPLICIT_FIELD_SYNTAX.matcher(query).find()) {
            return false;
        }

        String[] words = WHITESPACE.split(query.trim());
        if (words.length < MINIMUM_WORDS_FOR_UNDERSTANDING) {
            return false;
        }

        for (String word : words) {
            if (RELATIONAL_TERMS.contains(stripPunctuation(word))) {
                return true;
            }
        }
        // Long but with no relational words: a list of keywords, which lexical and vector retrieval
        // handle directly.
        return false;
    }

    private String stripPunctuation(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}

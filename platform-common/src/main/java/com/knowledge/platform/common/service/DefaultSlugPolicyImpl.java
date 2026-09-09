package com.knowledge.platform.common.service;

import com.knowledge.platform.common.model.Slug;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Default {@link SlugPolicy}.
 *
 * <p>See {@link SlugPolicy} for the policy itself and why it is a bean rather than a static helper.
 */
@Service
public class DefaultSlugPolicyImpl implements SlugPolicy {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

    @Override
    public Slug slugify(String text) {
        String normalized = normalizeOrNull(text);
        if (normalized == null) {
            throw new IllegalArgumentException("cannot derive a slug from: '" + text + "'");
        }
        return new Slug(normalized);
    }

    @Override
    public Slug slugifyOrNull(String text) {
        String normalized = normalizeOrNull(text);
        return normalized == null ? null : new Slug(normalized);
    }

    @Override
    public Slug referenceSlug(String linkTarget) {
        return slugify(linkTarget);
    }

    private String normalizeOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        String withoutMarks = DIACRITICS.matcher(decomposed).replaceAll("");
        String lower = withoutMarks.toLowerCase(Locale.ROOT);
        String hyphenated = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");
        String trimmed = EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = EDGE_HYPHENS.matcher(trimmed.substring(0, MAX_LENGTH)).replaceAll("");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}

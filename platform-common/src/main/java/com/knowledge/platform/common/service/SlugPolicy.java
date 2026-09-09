package com.knowledge.platform.common.service;

import com.knowledge.platform.common.model.Slug;

/**
 * The single, explicit definition of how human text becomes a {@link Slug}.
 *
 * <p>This is a bean rather than a static helper because link resolution, ingestion and the delivery
 * route resolver must all agree on the policy, and an injected dependency makes that agreement
 * visible. It is also what lets the policy be exercised directly in tests.
 *
 * <p>Policy: NFKD-normalize, drop diacritics, lowercase in the root locale, collapse any run of
 * non-alphanumeric characters to a single hyphen, trim edge hyphens, truncate to 160 characters.
 *
 * <p>The root locale matters: lowercasing in a Turkish locale maps {@code I} to a dotless {@code i},
 * which would make the same title produce different slugs on different machines.
 */
public interface SlugPolicy {

    /** Matches the column width shared by every slug column in the schema. */
    int MAX_LENGTH = 160;

    /**
     * Normalizes arbitrary text into slug form.
     *
     * @throws IllegalArgumentException if the text contains no slug-able characters
     */
    Slug slugify(String text);

    /** Same policy as {@link #slugify(String)}, returning {@code null} instead of throwing. */
    Slug slugifyOrNull(String text);

    /**
     * Resolves the slug an internal {@code [[Target]]} reference points at.
     *
     * <p>Reference targets are written as prose, so they go through exactly the same normalization as
     * titles. That identity is the whole of the link resolution policy: {@code [[Kubernetes
     * Networking]]}, {@code [[kubernetes networking]]} and an article titled "Kubernetes Networking"
     * all reduce to {@code kubernetes-networking}.
     */
    Slug referenceSlug(String linkTarget);
}

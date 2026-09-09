package com.knowledge.platform.common.model;

import java.util.Objects;

/**
 * A normalized, URL-safe identity for a piece of platform content.
 *
 * <p>Slugs are the stable public identity of articles, authors, topics and tags, and they are also
 * the resolution target for internal {@code [[Wiki Link]]} references. Wrapping the string in a type
 * keeps a raw title from being passed where a normalized slug is required -- a mistake that would
 * silently produce unresolvable links rather than a compile error.
 */
public record Slug(String value) implements Comparable<Slug> {

    public Slug {
        Objects.requireNonNull(value, "slug value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
    }

    public static Slug of(String value) {
        return new Slug(value);
    }

    @Override
    public int compareTo(Slug other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

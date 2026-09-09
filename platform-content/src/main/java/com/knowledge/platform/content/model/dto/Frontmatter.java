package com.knowledge.platform.content.model.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Typed access to a document's YAML frontmatter.
 *
 * <p>commonmark's frontmatter extension hands back {@code Map<String, List<String>>} -- every value
 * is a list of strings, whatever the author wrote. This wrapper is where that becomes typed, and
 * where the coercions live in one place instead of being repeated by each ingestion handler.
 *
 * <p>Keys are matched case-insensitively: authors write {@code Title} and {@code title}, and failing
 * ingestion over the difference would be hostile.
 */
public record Frontmatter(Map<String, List<String>> values) {

    public Frontmatter {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static Frontmatter empty() {
        return new Frontmatter(Map.of());
    }

    /** The first value for a key, or empty when absent or blank. */
    public Optional<String> first(String key) {
        return all(key).stream().filter(value -> !value.isBlank()).map(String::trim).findFirst();
    }

    /**
     * Every value for a key.
     *
     * <p>Handles both YAML list form and the comma-separated single-line form authors habitually
     * write ({@code tags: postgres, indexing}), because both are common in real repositories.
     */
    public List<String> all(String key) {
        List<String> raw = lookup(key);
        if (raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    /** A date value, or empty when absent or not an ISO date. */
    public Optional<LocalDate> date(String key) {
        return first(key).flatMap(value -> {
            try {
                return Optional.of(LocalDate.parse(value));
            } catch (DateTimeParseException e) {
                // Not an error: a malformed date is reported as an ingestion diagnostic by the
                // metadata handler, which has the run context needed to record it usefully.
                return Optional.empty();
            }
        });
    }

    /** A boolean value, treating the usual YAML spellings as true. */
    public boolean flag(String key, boolean defaultValue) {
        return first(key)
                .map(value -> switch (value.toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "y", "1", "on" -> true;
                    case "false", "no", "n", "0", "off" -> false;
                    default -> defaultValue;
                })
                .orElse(defaultValue);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    private List<String> lookup(String key) {
        List<String> exact = values.get(key);
        if (exact != null) {
            return exact;
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(List.of());
    }
}

package com.knowledge.platform.delivery.model.dto;

import java.util.List;

/**
 * The full set of topics or tags, with how much has been published under each.
 *
 * <p>Counts are included because a browse index without them forces the reader to click into an empty
 * subject to discover it is empty. Entries with nothing published are omitted for the same reason.
 */
public record TaxonomyListing(List<TaxonomyEntry> entries) {

    public TaxonomyListing {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record TaxonomyEntry(String slug, String name, String description, long articleCount) {
    }
}

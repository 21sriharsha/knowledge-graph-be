package com.knowledge.platform.content.model.entity;

/**
 * Where an article sits in its lifecycle.
 *
 * <p>Not a boolean: an archived article is not a draft, and the distinction decides whether its
 * inbound links still resolve and whether it stays in the search index.
 */
public enum PublicationState {
    /** Ingested and materialized, but not publicly readable. */
    DRAFT,
    /** Publicly readable. The only state the public read path will serve. */
    PUBLISHED,
    /** Withdrawn from publication, retained so that links to it are not silently orphaned. */
    ARCHIVED;

    public boolean isPublic() {
        return this == PUBLISHED;
    }
}

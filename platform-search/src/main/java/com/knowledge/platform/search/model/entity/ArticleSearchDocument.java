package com.knowledge.platform.search.model.entity;

import java.util.UUID;

/**
 * The derived lexical representation of an article, as stored.
 *
 * <p>A record rather than a JPA entity, and the search module uses JDBC throughout. That is a
 * deliberate choice, not a shortcut: this table's {@code search_vector} is a generated column that
 * Hibernate must never write, ranking needs {@code ts_rank_cd}, and the embeddings table alongside it
 * uses a pgvector type and the {@code <=>} operator. None of that is expressible in JPQL, and mapping
 * half of it through JPA would mean two persistence styles in one module for no benefit.
 *
 * @param metadataText author name, tags and topics flattened, so a query naming an author still
 *     matches lexically even before the planner applies an author filter
 * @param contentHash what was indexed, making a stale index detectable
 */
public record ArticleSearchDocument(
        UUID articleId,
        String titleText,
        String summaryText,
        String bodyText,
        String metadataText,
        String contentHash) {
}

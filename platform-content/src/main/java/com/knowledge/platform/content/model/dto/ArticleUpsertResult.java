package com.knowledge.platform.content.model.dto;

import com.knowledge.platform.content.model.entity.Article;

/**
 * The outcome of storing an article revision.
 *
 * <p>{@code contentChanged} is what the ingestion pipeline branches on. When the incoming bytes hash
 * to what is already stored, every derived representation is still valid, and the remaining handlers
 * -- graph, search, embedding, read model, cache -- can be skipped entirely. That is the difference
 * between a replayed webhook being harmless and a replayed webhook being free.
 *
 * <p>{@code derivedMetadataRefreshed} covers the one exception: the source bytes are unchanged but the
 * platform now derives a different title or summary from them (a parser fix). No new revision is
 * recorded -- the source did not change -- but the derived representations must be rebuilt, or the
 * corrected values would never reach readers.
 */
public record ArticleUpsertResult(Article article, boolean created, boolean contentChanged, boolean derivedMetadataRefreshed) {

    public ArticleUpsertResult(Article article, boolean created, boolean contentChanged) {
        this(article, created, contentChanged, false);
    }

    public boolean requiresRematerialization() {
        return created || contentChanged || derivedMetadataRefreshed;
    }
}

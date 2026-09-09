package com.knowledge.platform.content.model.dto;

import com.knowledge.platform.content.model.entity.Article;

/**
 * The outcome of storing an article revision.
 *
 * <p>{@code contentChanged} is what the ingestion pipeline branches on. When the incoming bytes hash
 * to what is already stored, every derived representation is still valid, and the remaining handlers
 * -- graph, search, embedding, read model, cache -- can be skipped entirely. That is the difference
 * between a replayed webhook being harmless and a replayed webhook being free.
 */
public record ArticleUpsertResult(Article article, boolean created, boolean contentChanged) {

    public boolean requiresRematerialization() {
        return created || contentChanged;
    }
}

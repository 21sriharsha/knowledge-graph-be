package com.knowledge.platform.content.model.dto;

import com.knowledge.platform.content.model.entity.PublicationState;
import java.util.UUID;

/**
 * Published when an article's publication state changes outside the ingestion pipeline.
 *
 * <p>Ingestion rebuilds every derived representation as part of its chain, but a studio
 * publish/unpublish/archive touches only canonical content. Without this event the derived state
 * silently disagrees with it: the article vanishes from listings and search — which query the
 * database directly — while its cached read model and its route entry keep serving it. An
 * "unpublished" article therefore stayed publicly readable, which is a correctness and privacy bug
 * rather than a staleness inconvenience.
 *
 * <p>An event rather than a direct call because the modules that must react -- delivery, and any
 * future consumer -- depend on content, not the reverse. Content states that something changed; it
 * does not need to know who cares.
 */
public record ArticlePublicationChangedEvent(UUID articleId, String slug, PublicationState state) {

    public boolean isPublic() {
        return state.isPublic();
    }
}

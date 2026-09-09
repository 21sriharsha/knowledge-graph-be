package com.knowledge.platform.author.model.dto;

import com.knowledge.platform.author.model.entity.Author;
import java.util.UUID;

/**
 * The author fields other modules embed in their own read models.
 *
 * <p>Exists so that content, search and delivery can show an article's author without holding an
 * {@code Author} entity, which would tie them to the author module's persistence model and to
 * whichever transaction loaded it.
 */
public record AuthorSummary(UUID id, String slug, String displayName, String avatarUrl) {

    public static AuthorSummary from(Author author) {
        return new AuthorSummary(
                author.getId(), author.getSlug(), author.getDisplayName(), author.getAvatarUrl());
    }
}

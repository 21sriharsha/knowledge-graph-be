package com.knowledge.platform.author.model.response;

import com.knowledge.platform.author.model.entity.Author;
import java.time.Instant;
import java.util.UUID;

/** The author representation returned by author endpoints. */
public record AuthorResponse(
        UUID id,
        String slug,
        String displayName,
        String biography,
        String avatarUrl,
        Instant createdAt) {

    public static AuthorResponse from(Author author) {
        // Email is deliberately absent: it is a contact detail held for source attribution and
        // deduplication, not part of a public profile.
        return new AuthorResponse(
                author.getId(),
                author.getSlug(),
                author.getDisplayName(),
                author.getBiography(),
                author.getAvatarUrl(),
                author.getCreatedAt());
    }
}

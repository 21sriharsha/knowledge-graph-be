package com.knowledge.platform.author.model.dto;

import java.util.UUID;

/**
 * A byline's profile changed: its display name, biography, avatar or email.
 *
 * <p>Published so modules that copy profile details into derived representations -- delivery's
 * article read models embed the author's name -- can rebuild them. Listeners act after commit, so a
 * rolled-back edit never reaches a reader.
 */
public record AuthorProfileChangedEvent(UUID authorId, String slug) {
}

package com.knowledge.platform.author.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Which byline an account writes under. */
public record LinkAuthorRequest(@NotNull UUID authorId) {
}

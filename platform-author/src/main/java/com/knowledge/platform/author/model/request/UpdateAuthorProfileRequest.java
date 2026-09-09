package com.knowledge.platform.author.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Studio payload for editing an author's public profile. */
public record UpdateAuthorProfileRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 10_000) String biography,
        @Size(max = 1000) String avatarUrl,
        @Email @Size(max = 320) String email) {
}

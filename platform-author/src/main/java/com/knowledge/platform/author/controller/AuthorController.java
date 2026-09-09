package com.knowledge.platform.author.controller;

import com.knowledge.platform.author.delegate.AuthorDelegate;
import com.knowledge.platform.author.model.request.UpdateAuthorProfileRequest;
import com.knowledge.platform.author.model.response.AuthorResponse;
import com.knowledge.platform.common.model.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author identity endpoints.
 *
 * <p>Reads are public. Writes live under {@code /api/studio}, which the security configuration
 * requires the AUTHOR role for -- the path prefix is the authorization boundary, so a new studio
 * endpoint is protected by default rather than by remembering to annotate it.
 */
@RestController
@Tag(name = "Authors", description = "Author identity and profiles")
public class AuthorController {

    private final AuthorDelegate authorDelegate;

    public AuthorController(AuthorDelegate authorDelegate) {
        this.authorDelegate = authorDelegate;
    }

    @GetMapping("/api/authors/{slug}")
    @Operation(summary = "Fetch an author's public profile")
    public AuthorResponse getAuthor(@PathVariable String slug) {
        return authorDelegate.getBySlug(slug);
    }

    @GetMapping("/api/authors")
    @Operation(summary = "List authors")
    public PageResponse<AuthorResponse> listAuthors(Pageable pageable) {
        return authorDelegate.list(pageable);
    }

    @PutMapping("/api/studio/authors/{slug}")
    @Operation(summary = "Update an author's profile")
    public AuthorResponse updateAuthor(
            @PathVariable String slug, @Valid @RequestBody UpdateAuthorProfileRequest request) {
        return authorDelegate.updateProfile(slug, request);
    }
}

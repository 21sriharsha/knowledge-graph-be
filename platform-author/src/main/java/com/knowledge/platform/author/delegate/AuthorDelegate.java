package com.knowledge.platform.author.delegate;

import com.knowledge.platform.author.model.request.UpdateAuthorProfileRequest;
import com.knowledge.platform.author.model.response.AuthorResponse;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.common.model.Slug;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Translates between the HTTP contract and the author domain.
 *
 * <p>The delegate is where request records become domain arguments and entities become response
 * records. Keeping that here leaves controllers with routing and status codes only, and leaves the
 * service free of any knowledge that HTTP exists -- which is what lets ingestion call the same
 * service without dragging web types along.
 */
@Component
public class AuthorDelegate {

    private final AuthorService authorService;

    public AuthorDelegate(AuthorService authorService) {
        this.authorService = authorService;
    }

    public AuthorResponse getBySlug(String slug) {
        return AuthorResponse.from(authorService.requireBySlug(Slug.of(slug)));
    }

    public PageResponse<AuthorResponse> list(Pageable pageable) {
        return PageResponse.from(authorService.findAll(pageable), AuthorResponse::from);
    }

    public AuthorResponse updateProfile(String slug, UpdateAuthorProfileRequest request) {
        return AuthorResponse.from(authorService.updateProfile(
                Slug.of(slug),
                request.displayName(),
                request.biography(),
                request.avatarUrl(),
                request.email()));
    }
}

package com.knowledge.platform.author.delegate;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.model.request.UpdateAuthorProfileRequest;
import com.knowledge.platform.author.model.response.AuthorResponse;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import com.knowledge.platform.common.exception.NotFoundException;
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

    private final StudioPrincipalResolver principals;

    public AuthorDelegate(AuthorService authorService, StudioPrincipalResolver principals) {
        this.principals = principals;
        this.authorService = authorService;
    }

    public AuthorResponse getBySlug(String slug) {
        return AuthorResponse.from(authorService.requireBySlug(Slug.of(slug)));
    }

    public PageResponse<AuthorResponse> list(Pageable pageable) {
        return PageResponse.from(authorService.findAll(pageable), AuthorResponse::from);
    }

    /**
     * Updates an author's profile.
     *
     * <p>Your own, unless you are an administrator. A profile is a byline: the display name, avatar
     * and biography shown against everything that author has published, so editing someone else's
     * is a way to publish under their name without writing anything.
     */
    public AuthorResponse updateProfile(String slug, UpdateAuthorProfileRequest request) {
        StudioPrincipal principal = principals.require();
        Author target = authorService.requireBySlug(Slug.of(slug));
        if (!principal.canActOnBehalfOf(target.getId())) {
            throw NotFoundException.of("Author", slug);
        }
        return AuthorResponse.from(authorService.updateProfile(
                Slug.of(slug),
                request.displayName(),
                request.biography(),
                request.avatarUrl(),
                request.email()));
    }
}

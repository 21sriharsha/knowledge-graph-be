package com.knowledge.platform.content.delegate;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.response.ArticleResponse;
import com.knowledge.platform.content.service.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Translates between the studio HTTP contract and the content domain.
 *
 * <p>Editorial control is scoped to the caller's own byline. Publication state is the most
 * consequential thing an author can change -- unpublishing someone else's article removes it from
 * the site and, because routes and read models follow, from every link to it.
 *
 * <p>The whole studio surface for articles is here, and every operation resolves the article through
 * an ownership-aware lookup. Reaching {@code requireBySlug} directly from this class would be the
 * bug worth reviewing for.
 */
@Component
public class ArticleDelegate {

    private final ArticleService articleService;
    private final StudioPrincipalResolver principals;

    public ArticleDelegate(ArticleService articleService, StudioPrincipalResolver principals) {
        this.articleService = articleService;
        this.principals = principals;
    }

    public ArticleResponse getBySlug(String slug) {
        return ArticleResponse.from(
                articleService.requireOwnedBySlug(Slug.of(slug), principals.require()));
    }

    /**
     * The articles this caller may edit.
     *
     * <p>Scoped rather than filtered after the fact, so paging stays honest: filtering a page of
     * twenty down to the three you own would report a page size nobody asked for and a total that
     * counts other people's work.
     */
    public PageResponse<ArticleResponse> listVisible(Pageable pageable) {
        StudioPrincipal principal = principals.require();

        if (principal.isAdmin()) {
            return PageResponse.from(articleService.findPublished(pageable), ArticleResponse::from);
        }
        if (principal.authorId() == null) {
            // Signed in, no byline: nothing is theirs to edit.
            return PageResponse.from(Page.<Article>empty(pageable), ArticleResponse::from);
        }
        return PageResponse.from(
                articleService.findPublishedByAuthor(principal.authorId(), pageable),
                ArticleResponse::from);
    }

    public ArticleResponse publish(String slug) {
        Slug target = requireOwned(slug);
        return ArticleResponse.from(articleService.publish(target));
    }

    public ArticleResponse unpublish(String slug) {
        Slug target = requireOwned(slug);
        return ArticleResponse.from(articleService.unpublish(target));
    }

    public ArticleResponse archive(String slug) {
        Slug target = requireOwned(slug);
        return ArticleResponse.from(articleService.archive(target));
    }

    private Slug requireOwned(String slug) {
        Slug target = Slug.of(slug);
        articleService.requireOwnedBySlug(target, principals.require());
        return target;
    }
}

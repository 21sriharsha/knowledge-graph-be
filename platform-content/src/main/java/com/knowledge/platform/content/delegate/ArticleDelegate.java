package com.knowledge.platform.content.delegate;

import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.response.ArticleResponse;
import com.knowledge.platform.content.service.ArticleService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Translates between the studio HTTP contract and the content domain. */
@Component
public class ArticleDelegate {

    private final ArticleService articleService;

    public ArticleDelegate(ArticleService articleService) {
        this.articleService = articleService;
    }

    public ArticleResponse getBySlug(String slug) {
        return ArticleResponse.from(articleService.requireBySlug(Slug.of(slug)));
    }

    public PageResponse<ArticleResponse> listPublished(Pageable pageable) {
        return PageResponse.from(articleService.findPublished(pageable), ArticleResponse::from);
    }

    public ArticleResponse publish(String slug) {
        return ArticleResponse.from(articleService.publish(Slug.of(slug)));
    }

    public ArticleResponse unpublish(String slug) {
        return ArticleResponse.from(articleService.unpublish(Slug.of(slug)));
    }

    public ArticleResponse archive(String slug) {
        return ArticleResponse.from(articleService.archive(Slug.of(slug)));
    }
}

package com.knowledge.platform.content.controller;

import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.content.delegate.ArticleDelegate;
import com.knowledge.platform.content.model.response.ArticleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editorial article management.
 *
 * <p>All of it lives under {@code /api/studio}, which the security configuration gates on the AUTHOR
 * role. The public article endpoint is not here: reading an article is served by the delivery
 * module from a read model, which is a different concern with a different shape.
 */
@RestController
@RequestMapping("/api/studio/articles")
@Tag(name = "Studio: Articles", description = "Editorial article management")
public class ArticleController {

    private final ArticleDelegate articleDelegate;

    public ArticleController(ArticleDelegate articleDelegate) {
        this.articleDelegate = articleDelegate;
    }

    @GetMapping
    @Operation(summary = "Published articles this account may edit")
    public PageResponse<ArticleResponse> list(Pageable pageable) {
        return articleDelegate.listVisible(pageable);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Fetch an article in its editorial form, whatever its publication state")
    public ArticleResponse get(@PathVariable String slug) {
        return articleDelegate.getBySlug(slug);
    }

    @PostMapping("/{slug}/publish")
    @Operation(summary = "Publish an article")
    public ArticleResponse publish(@PathVariable String slug) {
        return articleDelegate.publish(slug);
    }

    @PostMapping("/{slug}/unpublish")
    @Operation(summary = "Withdraw an article from publication")
    public ArticleResponse unpublish(@PathVariable String slug) {
        return articleDelegate.unpublish(slug);
    }

    @PostMapping("/{slug}/archive")
    @Operation(summary = "Archive an article, retaining it so inbound links are not orphaned")
    public ArticleResponse archive(@PathVariable String slug) {
        return articleDelegate.archive(slug);
    }
}

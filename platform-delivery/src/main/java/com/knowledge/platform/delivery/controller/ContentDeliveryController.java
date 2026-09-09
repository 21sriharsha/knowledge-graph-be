package com.knowledge.platform.delivery.controller;

import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.delivery.delegate.DeliveryDelegate;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import com.knowledge.platform.delivery.model.dto.ArticleReference;
import com.knowledge.platform.delivery.model.dto.AuthorReadModel;
import com.knowledge.platform.delivery.model.dto.NavigationModel;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import com.knowledge.platform.delivery.model.dto.TagReadModel;
import com.knowledge.platform.delivery.model.dto.TaxonomyListing;
import com.knowledge.platform.delivery.model.dto.TopicReadModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public read path: everything the SSR frontend renders a page from.
 *
 * <p>Each endpoint returns a complete read model, so a page needs one call rather than a cascade of
 * follow-ups for the author, the tags, the related articles and the graph. The responses are JSON;
 * the backend generates no HTML, and the article body arrives as a structured block tree for the
 * frontend to render with its own components.
 */
@RestController
@Tag(name = "Delivery", description = "Read models for the public reading experience")
public class ContentDeliveryController {

    private final DeliveryDelegate deliveryDelegate;

    public ContentDeliveryController(DeliveryDelegate deliveryDelegate) {
        this.deliveryDelegate = deliveryDelegate;
    }

    @GetMapping("/api/articles/{slug}")
    @Operation(summary = "The complete article page read model",
            description = """
                    Includes body blocks, outline, author, taxonomy, related articles, backlinks,
                    breadcrumbs, previous/next navigation and a bounded graph neighbourhood --
                    everything a normal article page needs, with no follow-up calls.
                    """)
    public ArticleReadModel article(@PathVariable String slug) {
        return deliveryDelegate.article(slug);
    }

    @GetMapping("/api/authors/{slug}/page")
    @Operation(summary = "The author page read model")
    public AuthorReadModel author(@PathVariable String slug) {
        return deliveryDelegate.author(slug);
    }

    @GetMapping("/api/topics/{slug}")
    @Operation(summary = "The topic landing page read model")
    public TopicReadModel topic(@PathVariable String slug) {
        return deliveryDelegate.topic(slug);
    }

    @GetMapping("/api/tags/{slug}")
    @Operation(summary = "The tag landing page read model")
    public TagReadModel tag(@PathVariable String slug) {
        return deliveryDelegate.tag(slug);
    }

    @GetMapping("/api/topics")
    @Operation(summary = "Every topic with published content",
            description = "Topics with nothing published are omitted: a browse index that lists "
                    + "empty subjects makes the reader discover the emptiness by clicking.")
    public TaxonomyListing topics() {
        return deliveryDelegate.topics();
    }

    @GetMapping("/api/tags")
    @Operation(summary = "Every tag with published content")
    public TaxonomyListing tags() {
        return deliveryDelegate.tags();
    }

    @GetMapping("/api/articles")
    @Operation(summary = "The article archive, newest first")
    public PageResponse<ArticleReference> articles(Pageable pageable) {
        return deliveryDelegate.articles(pageable);
    }

    @GetMapping("/api/navigation")
    @Operation(summary = "Site navigation")
    public NavigationModel navigation() {
        return deliveryDelegate.navigation();
    }

    @GetMapping("/api/routes/resolve")
    @Operation(summary = "Resolve a public URL path to what lives at it",
            description = """
                    The backend owns URL structure, so the frontend asks what a path is rather than
                    inferring it from path shape. That is what lets an article be renamed without the
                    frontend needing to know.
                    """)
    public RouteTarget resolveRoute(@RequestParam String path) {
        return deliveryDelegate.resolveRoute(path);
    }
}

package com.knowledge.platform.delivery.delegate;

import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import com.knowledge.platform.delivery.model.dto.ArticleReference;
import com.knowledge.platform.delivery.model.dto.AuthorReadModel;
import com.knowledge.platform.delivery.model.dto.NavigationModel;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import com.knowledge.platform.delivery.model.dto.TagReadModel;
import com.knowledge.platform.delivery.model.dto.TaxonomyListing;
import com.knowledge.platform.delivery.model.dto.TopicReadModel;
import com.knowledge.platform.delivery.service.BrowseReadModelService;
import com.knowledge.platform.delivery.service.ReadModelService;
import com.knowledge.platform.delivery.service.RouteResolutionService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Translates between the public HTTP contract and the delivery domain. */
@Component
public class DeliveryDelegate {

    private final ReadModelService readModelService;
    private final BrowseReadModelService browseReadModelService;
    private final RouteResolutionService routeResolutionService;

    public DeliveryDelegate(
            ReadModelService readModelService,
            BrowseReadModelService browseReadModelService,
            RouteResolutionService routeResolutionService) {
        this.readModelService = readModelService;
        this.browseReadModelService = browseReadModelService;
        this.routeResolutionService = routeResolutionService;
    }

    public ArticleReadModel article(String slug) {
        return readModelService.articleBySlug(Slug.of(slug));
    }

    public AuthorReadModel author(String slug) {
        return browseReadModelService.authorBySlug(Slug.of(slug));
    }

    public TopicReadModel topic(String slug) {
        return browseReadModelService.topicBySlug(Slug.of(slug));
    }

    public TagReadModel tag(String slug) {
        return browseReadModelService.tagBySlug(Slug.of(slug));
    }

    public TaxonomyListing topics() {
        return browseReadModelService.listTopics();
    }

    public TaxonomyListing tags() {
        return browseReadModelService.listTags();
    }

    public PageResponse<ArticleReference> articles(Pageable pageable) {
        return browseReadModelService.listArticles(pageable);
    }

    public NavigationModel navigation() {
        return browseReadModelService.navigation();
    }

    public RouteTarget resolveRoute(String path) {
        return routeResolutionService.resolve(path);
    }
}

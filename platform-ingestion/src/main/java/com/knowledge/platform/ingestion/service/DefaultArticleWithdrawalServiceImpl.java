package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import com.knowledge.platform.delivery.service.ReadModelService;
import com.knowledge.platform.delivery.service.RouteResolutionService;
import com.knowledge.platform.graph.service.LinkResolutionService;
import com.knowledge.platform.graph.service.RelationshipSuggestionService;
import com.knowledge.platform.search.service.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default ArticleWithdrawalService.
 *
 * <p>See {@link ArticleWithdrawalService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultArticleWithdrawalServiceImpl implements ArticleWithdrawalService {

    private final ArticleService articleService;
    private final LinkResolutionService linkResolutionService;
    private final RelationshipSuggestionService suggestionService;
    private final SearchIndexService searchIndexService;
    private final ReadModelService readModelService;
    private final RouteResolutionService routeResolutionService;

    public DefaultArticleWithdrawalServiceImpl(
            ArticleService articleService,
            LinkResolutionService linkResolutionService,
            RelationshipSuggestionService suggestionService,
            SearchIndexService searchIndexService,
            ReadModelService readModelService,
            RouteResolutionService routeResolutionService) {
        this.articleService = articleService;
        this.linkResolutionService = linkResolutionService;
        this.suggestionService = suggestionService;
        this.searchIndexService = searchIndexService;
        this.readModelService = readModelService;
        this.routeResolutionService = routeResolutionService;
    }

    @Override
    @Transactional
    public void withdraw(Article article) {
        articleService.archive(Slug.of(article.getSlug()));
        linkResolutionService.unresolveLinksTo(article.getId());
        suggestionService.removeFor(article.getId());
        searchIndexService.remove(article.getId());
        readModelService.remove(article.getId());
        routeResolutionService.unregister(RouteTarget.TargetType.ARTICLE, article.getId());
        log.info("Withdrew article '{}' after its source file was deleted", article.getSlug());
    }
}

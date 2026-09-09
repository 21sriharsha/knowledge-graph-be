package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.graph.model.entity.ArticleLink;
import com.knowledge.platform.graph.service.LinkResolutionService;
import com.knowledge.platform.graph.service.RelationshipSuggestionService;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the article's graph edges.
 *
 * <p>Three things happen, and the third is what makes the graph self-healing:
 *
 * <ol>
 *   <li>The article's outbound links are replaced with what its current content says.
 *   <li>References that did not resolve are recorded as diagnostics, so the author is told rather
 *       than left to notice a dead link on the page.
 *   <li>Every dangling reference <em>to</em> this article is bound to it. A link written before its
 *       target existed would otherwise stay broken until the referring article happened to be
 *       re-ingested -- which, for a stable document, might be never.
 * </ol>
 */
@Component
@Order(HandlerOrder.GRAPH_UPDATE)
public class GraphUpdateHandler implements IngestionHandler {

    /** Bounds the candidate set for suggestion scoring; suggestions are a nicety, not a survey. */
    private static final int SUGGESTION_CANDIDATE_LIMIT = 200;

    private final LinkResolutionService linkResolutionService;
    private final RelationshipSuggestionService suggestionService;
    private final ArticleService articleService;

    public GraphUpdateHandler(
            LinkResolutionService linkResolutionService,
            RelationshipSuggestionService suggestionService,
            ArticleService articleService) {
        this.linkResolutionService = linkResolutionService;
        this.suggestionService = suggestionService;
        this.articleService = articleService;
    }

    @Override
    public String name() {
        return "update-graph";
    }

    @Override
    public void handle(IngestionContext context) {
        Article article = context.requireArticle();

        List<String> unresolved = linkResolutionService.replaceOutboundLinks(
                article.getId(), context.requireDocument().links());

        unresolved.forEach(reference -> context.addDiagnostic(
                IngestionSeverity.WARNING, IngestionEvent.CODE_UNRESOLVED_LINK,
                "'[[" + reference + "]]' does not match any article. The link is kept and will "
                        + "resolve automatically if an article with that title is published."));

        // Articles this one now points AT are affected too: their backlink lists just gained an
        // entry. Missing this is a silent staleness bug — the target's own content is unchanged, so
        // nothing else will ever cause it to be rebuilt, and it displays an incomplete set of
        // backlinks permanently. It shows up constantly once authors keep separate repositories,
        // because cross-repository links land whenever the other repository happens to sync.
        linkResolutionService.outboundLinks(article.getId()).stream()
                .filter(ArticleLink::isResolved)
                .map(ArticleLink::getTargetArticleId)
                .distinct()
                .map(articleService::findById)
                .flatMap(java.util.Optional::stream)
                .forEach(target -> context.invalidateArticle(target.getSlug()));

        // Articles that already pointed here get their read models invalidated: their backlink lists
        // and link resolution states changed even though their own content did not.
        int repaired = linkResolutionService.resolvePendingLinksTo(article);
        if (repaired > 0) {
            context.addDiagnostic(IngestionSeverity.INFO, IngestionEvent.CODE_UNRESOLVED_LINK,
                    "Resolved " + repaired + " reference(s) that were waiting for this article");
        }
        linkResolutionService.backlinks(article.getId()).stream()
                .map(ArticleLink::getSourceArticleId)
                .distinct()
                .map(articleService::findById)
                .flatMap(java.util.Optional::stream)
                .forEach(source -> context.invalidateArticle(source.getSlug()));

        suggestionService.rebuildFor(article, candidatesFor(article));
    }

    /**
     * Candidates for suggestion scoring: recently published articles.
     *
     * <p>Bounded rather than the whole corpus. Scoring every article against every other is quadratic
     * work on the ingestion path for a feature whose value is a sidebar, and recency is a reasonable
     * proxy for what a reader is likely to want to see next.
     */
    private List<Article> candidatesFor(Article article) {
        return articleService.findPublished(
                        PageRequest.of(0, SUGGESTION_CANDIDATE_LIMIT))
                .getContent();
    }
}

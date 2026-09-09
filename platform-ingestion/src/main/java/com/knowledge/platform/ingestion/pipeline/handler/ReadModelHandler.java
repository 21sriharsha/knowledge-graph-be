package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.service.ReadModelService;
import com.knowledge.platform.delivery.service.RouteResolutionService;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Materializes the article's read model and registers its public routes.
 *
 * <p>This is where "publishing is a materialization event" pays off: the first reader of a newly
 * published article gets a prepared read model rather than paying to assemble one. Running it here
 * rather than lazily is the difference between a predictable publish cost and an unpredictable
 * first-request cost.
 *
 * <p>Runs after the graph handler because the read model embeds link resolution states, backlinks and
 * a graph neighbourhood -- all of which would be stale if assembled first.
 */
@Component
@Order(HandlerOrder.READ_MODEL)
public class ReadModelHandler implements IngestionHandler {

    private final ReadModelService readModelService;
    private final RouteResolutionService routeResolutionService;
    private final ArticleService articleService;

    public ReadModelHandler(
            ReadModelService readModelService,
            RouteResolutionService routeResolutionService,
            ArticleService articleService) {
        this.readModelService = readModelService;
        this.routeResolutionService = routeResolutionService;
        this.articleService = articleService;
    }

    @Override
    public String name() {
        return "build-read-model";
    }

    @Override
    public void handle(IngestionContext context) {
        // Routes first: an unpublished article's route is removed here, and materializing a read
        // model for something with no public URL would be wasted work.
        routeResolutionService.registerArticle(context.requireArticle());
        routeResolutionService.registerTaxonomy(
                List.copyOf(context.requireArticle().getTopics()),
                List.copyOf(context.requireArticle().getTags()));

        if (context.requireArticle().getPublicationState().isPublic()) {
            readModelService.materialize(context.requireArticle());
        } else {
            readModelService.remove(context.requireArticle().getId());
        }

        rebuildAffectedNeighbours(context);
    }

    /**
     * Rebuilds the read models of the other articles this document's links changed.
     *
     * <p>Evicting their cache entries is not enough. A persisted read model is only considered stale
     * when its article's own content hash changes, and a neighbour's content did not change — only
     * its backlinks did. Without an explicit rebuild the stored model is treated as current and the
     * missing backlink never appears.
     */
    private void rebuildAffectedNeighbours(IngestionContext context) {
        String own = context.requireArticle().getSlug();
        context.invalidatedArticleSlugs().stream()
                .filter(slug -> !slug.equals(own))
                .map(slug -> articleService.findBySlug(Slug.of(slug)))
                .flatMap(Optional::stream)
                .filter(article -> article.getPublicationState().isPublic())
                .forEach(readModelService::materialize);
    }
}

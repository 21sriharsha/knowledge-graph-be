package com.knowledge.platform.delivery.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.dto.ArticlePublicationChangedEvent;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the delivery layer in step when an article is published, unpublished or archived from the
 * studio.
 *
 * <p>Without this, canonical content and its derived representations diverge in the worst possible
 * direction: listings and search stop showing an unpublished article because they query the database,
 * while its cached read model and its route entry keep serving it. The article is then "unpublished"
 * everywhere except at its own URL — a correctness and privacy problem, not a staleness one, and the
 * route table has no TTL to eventually paper over it.
 *
 * <p>{@code AFTER_COMMIT} because acting on a state change that then rolls back would leave the
 * derived state describing an article revision that never existed.
 *
 * <p>{@code REQUIRES_NEW} is not optional here. An {@code AFTER_COMMIT} callback runs once the
 * original transaction has completed, but that transaction is still bound to the thread — so a
 * default {@code REQUIRED} would join a finished transaction and every write would fail with "no
 * active transaction for update or delete query". The listener is its own unit of work and has to
 * say so.
 */
@Slf4j
@Component
public class ArticlePublicationListener {

    private final ArticleService articleService;
    private final ReadModelService readModelService;
    private final RouteResolutionService routeResolutionService;
    private final ReadModelCacheInvalidator cacheInvalidator;

    public ArticlePublicationListener(
            ArticleService articleService,
            ReadModelService readModelService,
            RouteResolutionService routeResolutionService,
            ReadModelCacheInvalidator cacheInvalidator) {
        this.articleService = articleService;
        this.readModelService = readModelService;
        this.routeResolutionService = routeResolutionService;
        this.cacheInvalidator = cacheInvalidator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPublicationChanged(ArticlePublicationChangedEvent event) {
        log.debug("Article '{}' is now {}; updating derived representations", event.slug(), event.state());

        if (event.isPublic()) {
            articleService.findBySlug(Slug.of(event.slug())).ifPresent(article -> {
                routeResolutionService.registerArticle(article);
                // Materialize eagerly so the first reader of a newly published article gets a
                // prepared model rather than paying to assemble one.
                readModelService.materialize(article);
                invalidate(article);
            });
            return;
        }

        // Withdrawal order matters. The route goes first, so the URL stops resolving before anything
        // else; the read model and cache entry are then removed so nothing can serve it from behind.
        routeResolutionService.unregister(RouteTarget.TargetType.ARTICLE, event.articleId());
        readModelService.remove(event.articleId());
        articleService
                .findBySlug(Slug.of(event.slug()))
                .ifPresentOrElse(this::invalidate, () -> cacheInvalidator.afterArticleSlugChanged(event.slug()));
    }

    private void invalidate(Article article) {
        cacheInvalidator.afterArticleChanged(article, null, Set.of());
    }
}

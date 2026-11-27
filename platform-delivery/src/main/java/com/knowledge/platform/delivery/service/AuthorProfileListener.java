package com.knowledge.platform.delivery.service;

import com.knowledge.platform.author.model.dto.AuthorProfileChangedEvent;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps article pages in step when an author edits their profile.
 *
 * <p>Read models embed the author's display name, so without this a rename on the studio Profile
 * page would show on the author's own page and nowhere else until each article happened to be
 * re-ingested. Each published article's read model is re-materialized and its cache entries
 * invalidated, then the author's own page.
 *
 * <p>Paged, so an author with many articles is processed in bounded batches rather than loaded at
 * once. {@code AFTER_COMMIT} and {@code REQUIRES_NEW} for the same reasons as
 * {@link ArticlePublicationListener}: never act on an edit that rolls back, and never join the
 * finished transaction still bound to the thread.
 */
@Slf4j
@Component
public class AuthorProfileListener {

    private static final int BATCH_SIZE = 100;

    private final ArticleService articleService;
    private final ReadModelService readModelService;
    private final ReadModelCacheInvalidator cacheInvalidator;

    public AuthorProfileListener(
            ArticleService articleService,
            ReadModelService readModelService,
            ReadModelCacheInvalidator cacheInvalidator) {
        this.articleService = articleService;
        this.readModelService = readModelService;
        this.cacheInvalidator = cacheInvalidator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProfileChanged(AuthorProfileChangedEvent event) {
        int rebuilt = 0;
        Page<Article> page;
        int index = 0;
        do {
            page = articleService.findPublishedByAuthor(event.authorId(), PageRequest.of(index++, BATCH_SIZE));
            for (Article article : page.getContent()) {
                readModelService.materialize(article);
                cacheInvalidator.afterArticleChanged(article, event.slug(), Set.of());
                rebuilt++;
            }
        } while (page.hasNext());

        cacheInvalidator.afterAuthorChanged(event.slug());
        log.info("Author '{}' profile changed; rebuilt {} article read model(s)", event.slug(), rebuilt);
    }
}

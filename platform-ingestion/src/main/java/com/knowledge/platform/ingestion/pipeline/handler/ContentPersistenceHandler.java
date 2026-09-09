package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.dto.ArticleUpsertResult;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the canonical article. The point in the pipeline where content becomes real.
 *
 * <p>Transactional, and the only handler that is. Canonical content is the source of truth, so the
 * article, its revision row and its taxonomy commit together or not at all. Everything after this
 * point is derived and rebuildable, so those handlers deliberately run outside the transaction --
 * holding a database transaction open across an embedding call would be far worse than a derived
 * representation briefly lagging.
 *
 * <p>This is also where idempotency takes effect: when the content hash matches what is stored,
 * nothing downstream needs to run.
 */
@Component
@Order(HandlerOrder.CONTENT_PERSISTENCE)
public class ContentPersistenceHandler implements IngestionHandler {

    private static final String TAGS_KEY = "tags";
    private static final String TOPICS_KEY = "topics";
    private static final String SLUG_KEY = "slug";
    private static final String PUBLISHED_KEY = "published";
    private static final String DRAFT_KEY = "draft";

    private final ArticleService articleService;

    public ContentPersistenceHandler(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Override
    public String name() {
        return "persist-content";
    }

    @Override
    @Transactional
    public void handle(IngestionContext context) {
        CanonicalDocument document = context.requireDocument();

        ArticleUpsertResult result = articleService.upsert(new ArticleUpsertCommand(
                document.frontmatter().first(SLUG_KEY).orElse(null),
                document.title(),
                document.summary(),
                context.sourceFile().content(),
                context.contentHash(),
                context.requireAuthor().getId(),
                document.frontmatter().all(TAGS_KEY),
                topicsOf(document),
                document.wordCount(),
                document.readingTimeMinutes(),
                shouldPublish(document),
                new ArticleUpsertCommand.SourceCoordinate(
                        context.repository().getId(),
                        context.sourceFile().path(),
                        context.sourceFile().revision())));

        context.setArticle(result.article());
        context.setContentChanged(result.contentChanged());
        context.invalidateArticle(result.article().getSlug());

        if (!result.requiresRematerialization()) {
            context.skip("Content is unchanged since the last run; derived state is still valid");
        }
    }

    /** Topics fall back to tags so a repository that only uses one vocabulary still gets browsing. */
    private List<String> topicsOf(CanonicalDocument document) {
        List<String> topics = document.frontmatter().all(TOPICS_KEY);
        return topics.isEmpty() ? document.frontmatter().all(TAGS_KEY) : topics;
    }

    /**
     * Publication defaults to true.
     *
     * <p>A file committed to a connected content repository is, by the act of being committed, meant
     * to be published. Requiring {@code published: true} in every file would make connecting an
     * existing repository produce a site with nothing on it. {@code draft: true} is honoured as the
     * explicit opt-out, because that is the convention authors already know.
     */
    private boolean shouldPublish(CanonicalDocument document) {
        if (document.frontmatter().flag(DRAFT_KEY, false)) {
            return false;
        }
        return document.frontmatter().flag(PUBLISHED_KEY, true);
    }
}

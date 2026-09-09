package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.search.repository.ArticleEmbeddingRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default {@link EmbeddingBackfillService}.
 *
 * <p>Bounded per pass rather than draining the whole backlog at once. A cold start with a large
 * corpus would otherwise flood the bounded job executor, and every one of those jobs is an inference
 * call -- saturating the queue would starve the ingestion work that readers are actually waiting on.
 * Successive passes make progress instead.
 */
@Slf4j
@Service
public class DefaultEmbeddingBackfillServiceImpl implements EmbeddingBackfillService {

    private final ArticleEmbeddingRepository embeddings;
    private final SearchIndexService searchIndexService;
    private final ArticleService articleService;
    private final TextEmbeddingModel embeddingModel;
    private final SearchProperties properties;

    public DefaultEmbeddingBackfillServiceImpl(
            ArticleEmbeddingRepository embeddings,
            SearchIndexService searchIndexService,
            ArticleService articleService,
            TextEmbeddingModel embeddingModel,
            SearchProperties properties) {
        this.embeddings = embeddings;
        this.searchIndexService = searchIndexService;
        this.articleService = articleService;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public int backfill() {
        if (!embeddingModel.isAvailable()) {
            // Not a warning. Running with AI disabled is a supported configuration, and logging this
            // at anything louder than debug would fill the log of every such deployment.
            log.debug("Embedding provider unavailable; nothing to backfill");
            return 0;
        }

        List<ArticleEmbeddingRepository.ArticleBacklogEntry> backlog =
                embeddings.findMissingEmbeddings(properties.embeddingBatchSize());
        if (backlog.isEmpty()) {
            return 0;
        }

        int scheduled = 0;
        for (ArticleEmbeddingRepository.ArticleBacklogEntry entry : backlog) {
            // Re-derive the text from canonical Markdown rather than storing it: the search document
            // already holds an indexed copy, but canonical content is the source of truth and this
            // keeps the backfill correct even if the search document is itself stale.
            var article = articleService.findById(entry.articleId());
            if (article.isEmpty()) {
                continue;
            }
            searchIndexService.scheduleEmbedding(
                    entry.articleId(),
                    article.get().getCanonicalMarkdown(),
                    entry.contentHash());
            scheduled++;
        }

        log.info("Scheduled {} embedding backfill job(s)", scheduled);
        return scheduled;
    }

    @Override
    public long backlogSize() {
        // Counting via the same bounded query keeps one definition of "missing"; the cap means a very
        // large backlog reports as "at least the cap", which is enough for a health signal.
        return embeddings.findMissingEmbeddings(properties.embeddingBatchSize()).size();
    }
}

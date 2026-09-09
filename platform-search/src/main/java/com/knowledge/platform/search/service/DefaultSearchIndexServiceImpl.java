package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.jobs.model.RetryPolicy;
import com.knowledge.platform.jobs.service.Job;
import com.knowledge.platform.jobs.service.JobQueue;
import com.knowledge.platform.search.model.entity.ArticleEmbedding;
import com.knowledge.platform.search.model.entity.ArticleSearchDocument;
import com.knowledge.platform.search.repository.ArticleEmbeddingRepository;
import com.knowledge.platform.search.repository.ArticleSearchDocumentRepository;
import java.time.Duration;
import java.util.StringJoiner;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default SearchIndexService.
 *
 * <p>See {@link SearchIndexService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultSearchIndexServiceImpl implements SearchIndexService {

    private static final String EMBEDDING_JOB_TYPE = "article-embedding";

    private final ArticleSearchDocumentRepository searchDocuments;
    private final ArticleEmbeddingRepository embeddings;
    private final TextEmbeddingModel embeddingModel;
    private final JobQueue jobQueue;

    public DefaultSearchIndexServiceImpl(
            ArticleSearchDocumentRepository searchDocuments,
            ArticleEmbeddingRepository embeddings,
            TextEmbeddingModel embeddingModel,
            JobQueue jobQueue) {
        this.searchDocuments = searchDocuments;
        this.embeddings = embeddings;
        this.embeddingModel = embeddingModel;
        this.jobQueue = jobQueue;
    }

    @Override
    @Transactional
    public void index(Article article, String plainText, String authorName) {
        searchDocuments.save(new ArticleSearchDocument(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                plainText,
                metadataTextFor(article, authorName),
                article.getContentHash()));
    }

    @Override
    public void scheduleEmbedding(UUID articleId, String plainText, String contentHash) {
        if (!embeddingModel.isAvailable()) {
            log.debug("No embedding provider available; article {} will have no vector until one is",
                    articleId);
            return;
        }
        if (embeddings.isEmbeddedAt(articleId, contentHash)) {
            return;
        }
        jobQueue.submit(new EmbeddingJob(articleId, plainText, contentHash));
    }

    @Override
    @Transactional
    public boolean generateEmbedding(UUID articleId, String plainText, String contentHash) {
        return embeddingModel.embed(plainText)
                .map(vector -> {
                    embeddings.save(new ArticleEmbedding(
                            articleId, embeddingModel.modelName(), vector, contentHash));
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void remove(UUID articleId) {
        searchDocuments.delete(articleId);
        embeddings.delete(articleId);
    }

    @Override
    public boolean isIndexedAt(UUID articleId, String contentHash) {
        return searchDocuments.isIndexedAt(articleId, contentHash);
    }

    /**
     * Author, topics and tags as searchable text, weighted C by the schema.
     *
     * <p>This is what lets a reader find an article by naming its author or subject in ordinary text,
     * without the query first having to be parsed into filters.
     */
    private String metadataTextFor(Article article, String authorName) {
        StringJoiner joiner = new StringJoiner(" ");
        if (authorName != null && !authorName.isBlank()) {
            joiner.add(authorName);
        }
        article.getTopics().forEach(topic -> joiner.add(topic.getName()));
        article.getTags().forEach(tag -> joiner.add(tag.getName()));
        return joiner.toString();
    }

    /**
     * Deferred embedding generation.
     *
     * <p>Idempotent by construction: the key is the article plus the content hash, so a job for
     * content already embedded collapses into the in-flight one, and re-running it simply overwrites
     * the same vector. Retries are safe for the same reason, so the policy asks for them -- an
     * embedding provider restarting should not permanently cost an article its vector.
     */
    private final class EmbeddingJob implements Job {

        private final UUID articleId;
        private final String plainText;
        private final String contentHash;

        private EmbeddingJob(UUID articleId, String plainText, String contentHash) {
            this.articleId = articleId;
            this.plainText = plainText;
            this.contentHash = contentHash;
        }

        @Override
        public String idempotencyKey() {
            return EMBEDDING_JOB_TYPE + ":" + articleId + ":" + contentHash;
        }

        @Override
        public String type() {
            return EMBEDDING_JOB_TYPE;
        }

        @Override
        public void execute() {
            if (!generateEmbedding(articleId, plainText, contentHash)) {
                // Throwing rather than returning quietly, so the retry policy engages. An unavailable
                // provider is exactly the transient failure retries exist for.
                throw new IllegalStateException(
                        "Embedding provider returned no vector for article " + articleId);
            }
        }

        @Override
        public RetryPolicy retryPolicy() {
            return RetryPolicy.exponential(4, Duration.ofSeconds(5));
        }
    }
}

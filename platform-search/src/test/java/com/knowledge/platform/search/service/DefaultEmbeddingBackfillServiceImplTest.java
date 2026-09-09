package com.knowledge.platform.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.search.repository.ArticleEmbeddingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Backfill exists because the reliability rules let an article publish without its vector. These
 * tests describe when it acts and, more importantly, when it stays out of the way.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultEmbeddingBackfillServiceImplTest {

    @Mock
    private ArticleEmbeddingRepository embeddings;

    @Mock
    private SearchIndexService searchIndexService;

    @Mock
    private ArticleService articleService;

    @Mock
    private TextEmbeddingModel embeddingModel;

    private EmbeddingBackfillService service;

    @BeforeEach
    void setUp() {
        service = new DefaultEmbeddingBackfillServiceImpl(
                embeddings, searchIndexService, articleService, embeddingModel,
                new SearchProperties(100, 50, 20, 500, 25));
        when(embeddingModel.isAvailable()).thenReturn(true);
    }

    @Test
    void schedulesEmbeddingsForArticlesMissingThem() {
        Article article = article();
        when(embeddings.findMissingEmbeddings(anyInt())).thenReturn(
                List.of(new ArticleEmbeddingRepository.ArticleBacklogEntry(
                        article.getId(), article.getContentHash())));
        when(articleService.findById(article.getId())).thenReturn(Optional.of(article));

        assertThat(service.backfill()).isEqualTo(1);

        verify(searchIndexService).scheduleEmbedding(
                eq(article.getId()), anyString(), eq(article.getContentHash()));
    }

    @Test
    @DisplayName("with AI switched off it does nothing; that is a supported configuration")
    void doesNothingWhenTheProviderIsUnavailable() {
        when(embeddingModel.isAvailable()).thenReturn(false);

        assertThat(service.backfill()).isZero();

        verify(embeddings, never()).findMissingEmbeddings(anyInt());
        verify(searchIndexService, never()).scheduleEmbedding(any(), anyString(), anyString());
    }

    @Test
    void doesNothingWhenNothingIsOutstanding() {
        when(embeddings.findMissingEmbeddings(anyInt())).thenReturn(List.of());

        assertThat(service.backfill()).isZero();

        verify(searchIndexService, never()).scheduleEmbedding(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("an article deleted between the query and the loop is skipped, not failed on")
    void skipsArticlesThatHaveSinceDisappeared() {
        UUID missing = UUID.randomUUID();
        when(embeddings.findMissingEmbeddings(anyInt())).thenReturn(
                List.of(new ArticleEmbeddingRepository.ArticleBacklogEntry(missing, "hash")));
        when(articleService.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.backfill()).isZero();
    }

    @Test
    @DisplayName("the backlog is drained in bounded passes, not all at once")
    void respectsTheConfiguredBatchSize() {
        service.backfill();

        // Each backfilled embedding is an inference call; an unbounded pass would saturate the job
        // executor and starve the ingestion work readers are waiting on.
        verify(embeddings).findMissingEmbeddings(25);
    }

    @Test
    void reportsTheBacklogSize() {
        when(embeddings.findMissingEmbeddings(anyInt())).thenReturn(List.of(
                new ArticleEmbeddingRepository.ArticleBacklogEntry(UUID.randomUUID(), "a"),
                new ArticleEmbeddingRepository.ArticleBacklogEntry(UUID.randomUUID(), "b")));

        assertThat(service.backlogSize()).isEqualTo(2);
    }

    private Article article() {
        return Article.create("kubernetes-networking", "Kubernetes Networking", "Summary",
                "# Kubernetes Networking\n\nBody.", "hash-1", UUID.randomUUID(), 100, 1);
    }
}

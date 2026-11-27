package com.knowledge.platform.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.common.service.DefaultSlugPolicyImpl;
import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.dto.ArticleUpsertResult;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.PublicationState;
import com.knowledge.platform.content.repository.ArticleRepository;
import com.knowledge.platform.content.repository.ArticleRevisionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Editorial list ordering (D1) and re-deriving metadata at an unchanged content hash (D2).
 */
class DefaultArticleServiceImplEditorialTest {

    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final ArticleRevisionRepository revisions = mock(ArticleRevisionRepository.class);
    private final TaxonomyService taxonomy = mock(TaxonomyService.class);
    private final DefaultArticleServiceImpl service = new DefaultArticleServiceImpl(
            articles, revisions, taxonomy, new DefaultSlugPolicyImpl(), mock(ApplicationEventPublisher.class));

    @Test
    @DisplayName("AC-3 without a sort the editorial list is most recently updated first")
    void defaultsToUpdatedAtDescending() {
        when(articles.findAllBy(any())).thenReturn(Page.empty());

        service.findEditable(PageRequest.of(2, 10));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(articles).findAllBy(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    @Test
    @DisplayName("AC-3 a caller-supplied sort is honoured")
    void honoursRequestedSort() {
        UUID author = UUID.randomUUID();
        Pageable requested = PageRequest.of(0, 20, Sort.by("title"));
        when(articles.findByAuthorId(author, requested)).thenReturn(Page.empty());

        service.findEditableByAuthor(author, requested);

        verify(articles).findByAuthorId(author, requested);
    }

    @Test
    @DisplayName("AC-5 same bytes, corrected summary: rematerialize without a new revision")
    void refreshesDerivedMetadataAtUnchangedHash() {
        Article existing = Article.create("hybrid-retrieval", "Hybrid Retrieval",
                "Combining lexical and semantic retrieval", "md", "hash-1", UUID.randomUUID(), 500, 3);
        existing.bindToSource(UUID.randomUUID(), "docs/hybrid-search.md");
        when(articles.findByRepositoryIdAndSourcePath(any(), any())).thenReturn(Optional.of(existing));
        when(articles.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleUpsertResult result = service.upsert(command(
                "Combining lexical and semantic retrieval, and why the scores must be normalized.", existing));

        assertThat(result.contentChanged()).isFalse();
        assertThat(result.derivedMetadataRefreshed()).isTrue();
        assertThat(result.requiresRematerialization()).isTrue();
        assertThat(result.article().getSummary()).endsWith("must be normalized.");
        assertThat(result.article().getRevisionNumber()).isEqualTo(1);
        assertThat(result.article().getPublicationState())
                .as("a refresh never changes publication state").isEqualTo(PublicationState.DRAFT);
        verify(revisions, never()).save(any());
        verify(taxonomy).resolveOrCreateTopics(any());
    }

    @Test
    @DisplayName("same bytes, same derived values: still a cheap no-op")
    void unchangedStaysUnchanged() {
        Article existing = Article.create("hybrid-retrieval", "Hybrid Retrieval", "Same summary", "md",
                "hash-1", UUID.randomUUID(), 500, 3);
        existing.bindToSource(UUID.randomUUID(), "docs/hybrid-search.md");
        when(articles.findByRepositoryIdAndSourcePath(any(), any())).thenReturn(Optional.of(existing));
        when(articles.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleUpsertResult result = service.upsert(command("Same summary", existing));

        assertThat(result.requiresRematerialization()).isFalse();
    }

    private static ArticleUpsertCommand command(String summary, Article existing) {
        return new ArticleUpsertCommand(null, "Hybrid Retrieval", summary, "md", "hash-1", existing.getAuthorId(),
                List.of(), List.of(), 500, 3, true,
                new ArticleUpsertCommand.SourceCoordinate(existing.getRepositoryId(), existing.getSourcePath(), "rev-2"));
    }
}

package com.knowledge.platform.content.delegate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.response.ArticleResponse;
import com.knowledge.platform.content.service.ArticleService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * The studio list uses the editorial queries (any publication state), scoped by principal (bug fix D1).
 */
class ArticleDelegateTest {

    private final ArticleService articleService = mock(ArticleService.class);
    private final StudioPrincipalResolver principals = mock(StudioPrincipalResolver.class);
    private final ArticleDelegate delegate = new ArticleDelegate(articleService, principals);
    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    @DisplayName("AC-1 an administrator gets every article, in any state")
    void adminListsAllEditableArticles() {
        when(principals.require()).thenReturn(StudioPrincipal.serviceAccount(Set.of(PlatformRole.ADMIN)));
        Article draft = article("draft-one");
        when(articleService.findEditable(pageable)).thenReturn(new PageImpl<>(List.of(draft), pageable, 1));

        PageResponse<ArticleResponse> page = delegate.listVisible(pageable);

        assertThat(page.content()).extracting(ArticleResponse::slug).containsExactly("draft-one");
        verify(articleService, never()).findPublished(any());
    }

    @Test
    @DisplayName("AC-2 an author gets only their own byline, in any state")
    void authorListsOwnEditableArticles() {
        UUID authorId = UUID.randomUUID();
        when(principals.require()).thenReturn(new StudioPrincipal(UUID.randomUUID(), authorId, Set.of(PlatformRole.AUTHOR)));
        when(articleService.findEditableByAuthor(authorId, pageable))
                .thenReturn(new PageImpl<>(List.of(article("mine")), pageable, 1));

        PageResponse<ArticleResponse> page = delegate.listVisible(pageable);

        assertThat(page.content()).extracting(ArticleResponse::slug).containsExactly("mine");
        verify(articleService, never()).findPublishedByAuthor(any(), any());
    }

    @Test
    @DisplayName("AC-2 an account without a byline gets an empty page and no query")
    void noBylineMeansNothingToEdit() {
        when(principals.require()).thenReturn(new StudioPrincipal(UUID.randomUUID(), null, Set.of(PlatformRole.AUTHOR)));

        PageResponse<ArticleResponse> page = delegate.listVisible(pageable);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        verifyNoInteractions(articleService);
    }

    private static Article article(String slug) {
        return Article.create(slug, "Title " + slug, "Summary", "# Title", "hash-" + slug, UUID.randomUUID(), 10, 1);
    }
}

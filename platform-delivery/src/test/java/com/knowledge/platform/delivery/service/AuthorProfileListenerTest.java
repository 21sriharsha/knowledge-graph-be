package com.knowledge.platform.delivery.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.dto.AuthorProfileChangedEvent;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Profile renames reach article pages (self-serve authoring, AC-9). */
class AuthorProfileListenerTest {

    private final ArticleService articles = mock(ArticleService.class);
    private final ReadModelService readModels = mock(ReadModelService.class);
    private final ReadModelCacheInvalidator caches = mock(ReadModelCacheInvalidator.class);
    private final AuthorProfileListener listener = new AuthorProfileListener(articles, readModels, caches);

    @Test
    @DisplayName("AC-9 every published article is re-materialized, page by page, then the author page")
    void rebuildsEveryPublishedArticle() {
        UUID authorId = UUID.randomUUID();
        Article first = mock(Article.class);
        Article second = mock(Article.class);
        when(articles.findPublishedByAuthor(eq(authorId), eq(PageRequest.of(0, 100))))
                .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 100), 101));
        when(articles.findPublishedByAuthor(eq(authorId), eq(PageRequest.of(1, 100))))
                .thenReturn(new PageImpl<>(List.of(second), PageRequest.of(1, 100), 101));

        listener.onProfileChanged(new AuthorProfileChangedEvent(authorId, "jane-doe"));

        verify(readModels).materialize(first);
        verify(readModels).materialize(second);
        verify(caches, times(2)).afterArticleChanged(any(), eq("jane-doe"), eq(Set.of()));
        verify(caches).afterAuthorChanged("jane-doe");
    }
}

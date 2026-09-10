package com.knowledge.platform.content.service;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.dto.ArticleUpsertResult;
import com.knowledge.platform.content.model.entity.Article;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The content module's application boundary: everything that reads or writes canonical articles.
 *
 * <p>Writes are transactional and cover the article, its revision row and its taxonomy together,
 * because a revision without its article, or an article whose tags half-applied, is a corrupt source
 * of truth rather than a stale derived representation.
 */
public interface ArticleService {

    Article requireBySlug(Slug slug);

    /**
     * Fetches a published article by slug.
     *
     * <p>The public read path uses this rather than {@link #requireBySlug}, so that an unpublished
     * article is indistinguishable from one that does not exist. Returning 403 instead of 404 would
     * confirm the slug to anyone guessing.
     */
    Article requirePublishedBySlug(Slug slug);

    Optional<Article> findBySlug(Slug slug);

    Optional<Article> findById(UUID id);

    List<Article> findBySlugs(List<String> slugs);

    /**
     * The article, if this caller is entitled to it.
     *
     * <p>The ownership check is inside the lookup so that forgetting it leaves nothing to act on.
     * A repository owned by somebody else is reported exactly as one that does not exist, because a
     * distinguishable refusal confirms the slug is real.
     */
    Article requireOwnedBySlug(Slug slug, StudioPrincipal principal);

    Page<Article> findPublished(Pageable pageable);

    Page<Article> findPublishedByAuthor(UUID authorId, Pageable pageable);

    Page<Article> findPublishedByTag(Slug tagSlug, Pageable pageable);

    Page<Article> findPublishedByTopic(Slug topicSlug, Pageable pageable);

    List<Article> findRelated(UUID articleId);

    List<Article> findByRepository(UUID repositoryId);

    Optional<Article> findPrevious(Instant publishedAt);

    Optional<Article> findNext(Instant publishedAt);

    /**
     * Stores a revision of an article, creating it if this is the first time it has been seen.
     *
     * <p>This is the single write path for ingested content, and the place idempotency is decided.
     * The article is located by source coordinate first and by slug second, so a retitled file
     * updates its existing article rather than creating a second one and orphaning every inbound
     * link to the original.
     */
    ArticleUpsertResult upsert(ArticleUpsertCommand command);

    Article publish(Slug slug);

    Article unpublish(Slug slug);

    Article archive(Slug slug);
}

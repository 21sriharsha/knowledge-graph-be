package com.knowledge.platform.content.service;

import com.knowledge.platform.common.exception.DomainRuleException;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.common.service.SlugPolicy;
import com.knowledge.platform.content.model.dto.ArticlePublicationChangedEvent;
import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.dto.ArticleUpsertResult;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.ArticleRevision;
import com.knowledge.platform.content.model.entity.PublicationState;
import com.knowledge.platform.content.repository.ArticleRepository;
import com.knowledge.platform.content.repository.ArticleRevisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default ArticleService.
 *
 * <p>See {@link ArticleService} for what this provides and why it exists.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class DefaultArticleServiceImpl implements ArticleService {

    /** Bounds the "related articles" query; the read model shows a handful, never a corpus. */
    private static final int RELATED_ARTICLE_LIMIT = 6;

    private final ArticleRepository articleRepository;
    private final ArticleRevisionRepository revisionRepository;
    private final TaxonomyService taxonomyService;
    private final SlugPolicy slugPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public DefaultArticleServiceImpl(
            ArticleRepository articleRepository,
            ArticleRevisionRepository revisionRepository,
            TaxonomyService taxonomyService,
            SlugPolicy slugPolicy,
            ApplicationEventPublisher eventPublisher) {
        this.articleRepository = articleRepository;
        this.revisionRepository = revisionRepository;
        this.taxonomyService = taxonomyService;
        this.slugPolicy = slugPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Article requireBySlug(Slug slug) {
        return articleRepository.findBySlug(slug.value())
                .orElseThrow(() -> NotFoundException.of("Article", slug.value()));
    }

    @Override
    public Article requirePublishedBySlug(Slug slug) {
        Article article = requireBySlug(slug);
        if (!article.getPublicationState().isPublic()) {
            throw NotFoundException.of("Article", slug.value());
        }
        return article;
    }

    @Override
    public Optional<Article> findBySlug(Slug slug) {
        return articleRepository.findBySlug(slug.value());
    }

    @Override
    public Optional<Article> findById(UUID id) {
        return articleRepository.findById(id);
    }

    @Override
    public List<Article> findBySlugs(List<String> slugs) {
        return slugs.isEmpty() ? List.of() : articleRepository.findBySlugIn(slugs);
    }

    @Override
    public Page<Article> findPublished(Pageable pageable) {
        return articleRepository.findByPublicationStateOrderByPublishedAtDesc(
                PublicationState.PUBLISHED, pageable);
    }

    @Override
    public Page<Article> findPublishedByAuthor(UUID authorId, Pageable pageable) {
        return articleRepository.findByAuthorIdAndPublicationStateOrderByPublishedAtDesc(
                authorId, PublicationState.PUBLISHED, pageable);
    }

    @Override
    public Page<Article> findPublishedByTag(Slug tagSlug, Pageable pageable) {
        return articleRepository.findByTagSlug(tagSlug.value(), PublicationState.PUBLISHED, pageable);
    }

    @Override
    public Page<Article> findPublishedByTopic(Slug topicSlug, Pageable pageable) {
        return articleRepository.findByTopicSlug(topicSlug.value(), PublicationState.PUBLISHED, pageable);
    }

    @Override
    public List<Article> findRelated(UUID articleId) {
        return articleRepository.findRelated(
                articleId, PublicationState.PUBLISHED, PageRequest.of(0, RELATED_ARTICLE_LIMIT));
    }

    @Override
    public List<Article> findByRepository(UUID repositoryId) {
        return articleRepository.findByRepositoryId(repositoryId);
    }

    @Override
    public Optional<Article> findPrevious(Instant publishedAt) {
        return articleRepository
                .findFirstByPublicationStateAndPublishedAtLessThanOrderByPublishedAtDesc(
                        PublicationState.PUBLISHED, publishedAt);
    }

    @Override
    public Optional<Article> findNext(Instant publishedAt) {
        return articleRepository
                .findFirstByPublicationStateAndPublishedAtGreaterThanOrderByPublishedAtAsc(
                        PublicationState.PUBLISHED, publishedAt);
    }

    @Override
    @Transactional
    public ArticleUpsertResult upsert(ArticleUpsertCommand command) {
        Slug slug = resolveSlug(command);
        Optional<Article> existing = locate(command, slug);

        if (existing.isEmpty()) {
            return create(command, slug);
        }

        Article article = existing.get();
        boolean changed = article.applyRevision(
                command.title(),
                command.summary(),
                command.canonicalMarkdown(),
                command.contentHash(),
                sourceRevisionOf(command),
                command.wordCount(),
                command.readingTimeMinutes());

        if (!changed) {
            log.debug("Article {} unchanged at hash {}; skipping rematerialization",
                    article.getSlug(), command.contentHash());
            return new ArticleUpsertResult(articleRepository.save(article), false, false);
        }

        if (!article.getSlug().equals(slug.value())) {
            requireSlugAvailable(slug, article.getId());
            article.rename(slug.value());
        }
        article.reassignAuthor(command.authorId());
        applyTaxonomy(article, command);
        applyPublicationState(article, command.publish());

        Article saved = articleRepository.save(article);
        recordRevision(saved);
        return new ArticleUpsertResult(saved, false, true);
    }

    @Override
    @Transactional
    public Article publish(Slug slug) {
        Article article = requireBySlug(slug);
        article.publish();
        Article saved = articleRepository.save(article);
        publishStateChange(saved);
        return saved;
    }

    @Override
    @Transactional
    public Article unpublish(Slug slug) {
        Article article = requireBySlug(slug);
        article.unpublish();
        Article saved = articleRepository.save(article);
        publishStateChange(saved);
        return saved;
    }

    @Override
    @Transactional
    public Article archive(Slug slug) {
        Article article = requireBySlug(slug);
        article.archive();
        Article saved = articleRepository.save(article);
        publishStateChange(saved);
        return saved;
    }

    private ArticleUpsertResult create(ArticleUpsertCommand command, Slug slug) {
        requireSlugAvailable(slug, null);
        Article article = Article.create(
                slug.value(),
                command.title(),
                command.summary(),
                command.canonicalMarkdown(),
                command.contentHash(),
                command.authorId(),
                command.wordCount(),
                command.readingTimeMinutes());
        if (command.sourceCoordinate() != null) {
            article.bindToSource(
                    command.sourceCoordinate().repositoryId(), command.sourceCoordinate().sourcePath());
            article.recordSourceRevision(command.sourceCoordinate().sourceRevision());
        }
        applyTaxonomy(article, command);
        applyPublicationState(article, command.publish());

        Article saved = articleRepository.save(article);
        recordRevision(saved);
        return new ArticleUpsertResult(saved, true, true);
    }

    private Optional<Article> locate(ArticleUpsertCommand command, Slug slug) {
        if (command.sourceCoordinate() != null) {
            Optional<Article> bySource = articleRepository.findByRepositoryIdAndSourcePath(
                    command.sourceCoordinate().repositoryId(),
                    command.sourceCoordinate().sourcePath());
            if (bySource.isPresent()) {
                return bySource;
            }
        }
        return articleRepository.findBySlug(slug.value());
    }

    private Slug resolveSlug(ArticleUpsertCommand command) {
        if (command.requestedSlug() != null && !command.requestedSlug().isBlank()) {
            return slugPolicy.slugify(command.requestedSlug());
        }
        return slugPolicy.slugify(command.title());
    }

    private void requireSlugAvailable(Slug slug, UUID allowedOwner) {
        Optional<Article> holder = articleRepository.findBySlug(slug.value());
        if (holder.isPresent() && !holder.get().getId().equals(allowedOwner)) {
            throw new DomainRuleException(
                    "Slug '" + slug.value() + "' is already used by another article");
        }
    }

    private void applyTaxonomy(Article article, ArticleUpsertCommand command) {
        article.replaceTags(taxonomyService.resolveOrCreateTags(command.tagNames()));
        article.replaceTopics(taxonomyService.resolveOrCreateTopics(command.topicNames()));
    }

    private void applyPublicationState(Article article, boolean publish) {
        if (publish) {
            article.publish();
        } else if (article.getPublicationState() == PublicationState.PUBLISHED) {
            // Frontmatter that stops saying "published" is an explicit retraction, so honour it.
            article.unpublish();
        }
    }

    /**
     * Appends the revision row, tolerating the case where it already exists.
     *
     * <p>The unique constraint on {@code (article_id, content_hash)} is the real guard. Checking
     * first avoids provoking a constraint violation that would mark the surrounding transaction for
     * rollback, which matters because ingestion writes several articles per run.
     */
    private void recordRevision(Article article) {
        if (revisionRepository.existsByArticleIdAndContentHash(
                article.getId(), article.getContentHash())) {
            return;
        }
        revisionRepository.save(ArticleRevision.of(article));
    }

    /**
     * Tells the rest of the platform that a publication state changed.
     *
     * <p>Only the studio path needs this. Ingestion rebuilds the derived representations itself as
     * part of the handler chain, so firing here as well would duplicate that work on every sync.
     */
    private void publishStateChange(Article article) {
        eventPublisher.publishEvent(new ArticlePublicationChangedEvent(
                article.getId(), article.getSlug(), article.getPublicationState()));
    }

    private String sourceRevisionOf(ArticleUpsertCommand command) {
        return command.sourceCoordinate() == null ? null : command.sourceCoordinate().sourceRevision();
    }
}

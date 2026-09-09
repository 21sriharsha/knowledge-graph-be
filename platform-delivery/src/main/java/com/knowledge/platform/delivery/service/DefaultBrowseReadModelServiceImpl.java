package com.knowledge.platform.delivery.service;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.content.service.TaxonomyService;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import com.knowledge.platform.delivery.model.dto.ArticleReference;
import com.knowledge.platform.delivery.model.dto.AuthorReadModel;
import com.knowledge.platform.delivery.model.dto.NavigationModel;
import com.knowledge.platform.delivery.model.dto.TagReadModel;
import com.knowledge.platform.delivery.model.dto.TaxonomyListing;
import com.knowledge.platform.delivery.model.dto.TopicReadModel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default BrowseReadModelService.
 *
 * <p>See {@link BrowseReadModelService} for what this provides and why it exists.
 */
@Service
@Transactional(readOnly = true)
public class DefaultBrowseReadModelServiceImpl implements BrowseReadModelService {

    /** Articles listed on an author or topic page before pagination is needed. */
    private static final int LISTING_SIZE = 50;

    private final AuthorService authorService;
    private final ArticleService articleService;
    private final TaxonomyService taxonomyService;

    public DefaultBrowseReadModelServiceImpl(
            AuthorService authorService,
            ArticleService articleService,
            TaxonomyService taxonomyService) {
        this.authorService = authorService;
        this.articleService = articleService;
        this.taxonomyService = taxonomyService;
    }

    @Override
    @Cacheable(cacheNames = "authorReadModels", key = "#slug.value()")
    public AuthorReadModel authorBySlug(Slug slug) {
        Author author = authorService.requireBySlug(slug);
        Page<Article> articles = articleService.findPublishedByAuthor(
                author.getId(), PageRequest.of(0, LISTING_SIZE));

        // The topics an author actually writes about, derived from their articles rather than
        // declared on the profile -- so it cannot drift from what they have published.
        Set<ArticleReadModel.TaxonomyReference> topics = new LinkedHashSet<>();
        articles.getContent().forEach(article -> article.getTopics().forEach(topic ->
                topics.add(new ArticleReadModel.TaxonomyReference(topic.getSlug(), topic.getName()))));

        return new AuthorReadModel(
                author.getSlug(),
                author.getDisplayName(),
                author.getBiography(),
                author.getAvatarUrl(),
                articles.getContent().stream()
                        .map(article -> toReference(article, author.getDisplayName()))
                        .toList(),
                List.copyOf(topics),
                articles.getTotalElements(),
                author.getCreatedAt());
    }

    @Override
    @Cacheable(cacheNames = "topicReadModels", key = "#slug.value()")
    public TopicReadModel topicBySlug(Slug slug) {
        Topic topic = taxonomyService.requireTopicBySlug(slug);
        Page<Article> articles = articleService.findPublishedByTopic(
                slug, PageRequest.of(0, LISTING_SIZE));
        return new TopicReadModel(
                topic.getSlug(),
                topic.getName(),
                topic.getDescription(),
                articles.getContent().stream()
                        .map(article -> toReference(article, authorNameOf(article)))
                        .toList(),
                articles.getTotalElements());
    }

    @Override
    @Cacheable(cacheNames = "navigation", key = "'site'")
    public NavigationModel navigation() {
        List<NavigationModel.NavigationEntry> topics = taxonomyService.findAllTopics().stream()
                .map(topic -> new NavigationModel.NavigationEntry(
                        topic.getName(), topic.getSlug(), "/topics/" + topic.getSlug(),
                        articleService.findPublishedByTopic(
                                Slug.of(topic.getSlug()), PageRequest.of(0, 1)).getTotalElements()))
                .filter(entry -> entry.articleCount() > 0)
                .toList();

        List<NavigationModel.NavigationEntry> authors = authorService
                .findAll(PageRequest.of(0, LISTING_SIZE)).getContent().stream()
                .map(author -> new NavigationModel.NavigationEntry(
                        author.getDisplayName(), author.getSlug(), "/authors/" + author.getSlug(),
                        articleService.findPublishedByAuthor(
                                author.getId(), PageRequest.of(0, 1)).getTotalElements()))
                .filter(entry -> entry.articleCount() > 0)
                .toList();

        long publishedCount = articleService.findPublished(PageRequest.of(0, 1)).getTotalElements();
        return new NavigationModel(topics, authors, publishedCount);
    }

    @Override
    @Cacheable(cacheNames = "tagReadModels", key = "#slug.value()")
    public TagReadModel tagBySlug(Slug slug) {
        Tag tag = taxonomyService.requireTagBySlug(slug);
        Page<Article> articles = articleService.findPublishedByTag(slug, PageRequest.of(0, LISTING_SIZE));
        return new TagReadModel(
                tag.getSlug(),
                tag.getName(),
                articles.getContent().stream()
                        .map(article -> toReference(article, authorNameOf(article)))
                        .toList(),
                articles.getTotalElements());
    }

    @Override
    @Cacheable(cacheNames = "taxonomyListings", key = "'topics'")
    public TaxonomyListing listTopics() {
        return new TaxonomyListing(taxonomyService.findAllTopics().stream()
                .map(topic -> new TaxonomyListing.TaxonomyEntry(
                        topic.getSlug(), topic.getName(), topic.getDescription(),
                        countByTopic(topic.getSlug())))
                .filter(entry -> entry.articleCount() > 0)
                .toList());
    }

    @Override
    @Cacheable(cacheNames = "taxonomyListings", key = "'tags'")
    public TaxonomyListing listTags() {
        return new TaxonomyListing(taxonomyService.findAllTags().stream()
                .map(tag -> new TaxonomyListing.TaxonomyEntry(
                        tag.getSlug(), tag.getName(), null, countByTag(tag.getSlug())))
                .filter(entry -> entry.articleCount() > 0)
                .toList());
    }

    @Override
    public PageResponse<ArticleReference> listArticles(Pageable pageable) {
        // Not cached: the archive is paged, so the cache key would be (page, size, sort) and the
        // long tail of pages nobody revisits would evict the entries that are actually hot.
        return PageResponse.from(articleService.findPublished(pageable),
                article -> toReference(article, authorNameOf(article)));
    }

    /**
     * Counts published articles under a taxonomy term.
     *
     * <p>A page request of size 1 read purely for its total: the listing needs the count, not the
     * rows, and fetching fifty articles per term to size a browse index would be gratuitous.
     */
    private long countByTopic(String slug) {
        return articleService.findPublishedByTopic(Slug.of(slug), PageRequest.of(0, 1))
                .getTotalElements();
    }

    private long countByTag(String slug) {
        return articleService.findPublishedByTag(Slug.of(slug), PageRequest.of(0, 1))
                .getTotalElements();
    }

    private String authorNameOf(Article article) {
        return authorService.findById(article.getAuthorId())
                .map(Author::getDisplayName)
                .orElse(null);
    }

    private ArticleReference toReference(Article article, String authorName) {
        return new ArticleReference(
                article.getSlug(), article.getTitle(), article.getSummary(), authorName);
    }
}

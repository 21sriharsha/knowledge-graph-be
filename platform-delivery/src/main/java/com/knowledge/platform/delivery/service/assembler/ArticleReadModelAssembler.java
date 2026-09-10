package com.knowledge.platform.delivery.service.assembler;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.content.markdown.CanonicalDocumentFactory;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import com.knowledge.platform.delivery.model.dto.ArticleReference;
import com.knowledge.platform.graph.model.dto.GraphNeighbourhood;
import com.knowledge.platform.graph.model.entity.ArticleLink;
import com.knowledge.platform.graph.service.GraphService;
import com.knowledge.platform.graph.service.LinkResolutionService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds the article read model from canonical content and its derived representations.
 *
 * <p>This is the expensive work the read-model architecture exists to move off the request path: it
 * re-parses Markdown, walks the graph, and fetches neighbours and navigation. Running it once when an
 * article changes rather than on every page view is the whole point, so it is called by ingestion and
 * by a cache miss, never by an ordinary request that finds a materialized model.
 *
 * <p>The internal links in the body are patched with their resolution state here. The content module
 * cannot know it -- only the graph module knows which targets exist -- so the block tree arrives with
 * every internal link marked unresolved and leaves with the truth.
 */
@Slf4j
@Component
public class ArticleReadModelAssembler {

    /** Depth of the graph context embedded in an article page: immediate neighbours only. */
    private static final int GRAPH_CONTEXT_DEPTH = 1;

    private final ArticleService articleService;
    private final AuthorService authorService;
    private final LinkResolutionService linkResolutionService;
    private final GraphService graphService;
    private final CanonicalDocumentFactory documentFactory;

    public ArticleReadModelAssembler(
            ArticleService articleService,
            AuthorService authorService,
            LinkResolutionService linkResolutionService,
            GraphService graphService,
            CanonicalDocumentFactory documentFactory) {
        this.articleService = articleService;
        this.authorService = authorService;
        this.linkResolutionService = linkResolutionService;
        this.graphService = graphService;
        this.documentFactory = documentFactory;
    }

    public ArticleReadModel assemble(Article article) {
        CanonicalDocument document = documentFactory.create(article.getCanonicalMarkdown());

        List<ArticleLink> outbound = linkResolutionService.outboundLinks(article.getId());
        List<ArticleLink> backlinks = linkResolutionService.backlinks(article.getId());

        Map<String, Boolean> resolutionBySlug = new HashMap<>();
        List<String> unresolved = new ArrayList<>();
        for (ArticleLink link : outbound) {
            resolutionBySlug.put(link.getTargetSlug(), link.isResolved());
            if (!link.isResolved()) {
                unresolved.add(link.getTargetReference());
            }
        }

        Author author = authorService.findById(article.getAuthorId()).orElse(null);

        return new ArticleReadModel(
                article.getSlug(),
                article.getTitle(),
                article.getSummary(),
                InternalLinkResolutionVisitor.applyTo(document.blocks(), resolutionBySlug, article),
                document.outline(),
                authorModel(author),
                article.getTopics().stream()
                        .map(topic -> new ArticleReadModel.TaxonomyReference(topic.getSlug(), topic.getName()))
                        .toList(),
                article.getTags().stream()
                        .map(tag -> new ArticleReadModel.TaxonomyReference(tag.getSlug(), tag.getName()))
                        .toList(),
                relatedReferences(article),
                backlinkReferences(backlinks),
                unresolved,
                breadcrumbsFor(article),
                article.getPublishedAt() == null ? null
                        : articleService.findPrevious(article.getPublishedAt())
                                .map(this::toReference).orElse(null),
                article.getPublishedAt() == null ? null
                        : articleService.findNext(article.getPublishedAt())
                                .map(this::toReference).orElse(null),
                graphContextFor(article),
                article.getWordCount(),
                article.getReadingTimeMinutes(),
                article.getPublishedAt(),
                article.getUpdatedAt());
    }

    private ArticleReadModel.AuthorSummaryModel authorModel(Author author) {
        if (author == null) {
            // An article always has an author by schema constraint, so this means the row vanished
            // between two reads. Rendering the page without a byline beats failing it.
            return new ArticleReadModel.AuthorSummaryModel(null, "Unknown", null);
        }
        return new ArticleReadModel.AuthorSummaryModel(
                author.getSlug(), author.getDisplayName(), author.getAvatarUrl());
    }

    private List<ArticleReference> relatedReferences(Article article) {
        return articleService.findRelated(article.getId()).stream().map(this::toReference).toList();
    }

    private List<ArticleReference> backlinkReferences(List<ArticleLink> backlinks) {
        return backlinks.stream()
                .map(ArticleLink::getSourceArticleId)
                .distinct()
                .map(articleService::findById)
                .flatMap(Optional::stream)
                .filter(source -> source.getPublicationState().isPublic())
                .map(this::toReference)
                .toList();
    }

    /**
     * The graph context embedded in the page.
     *
     * <p>Failure here is absorbed: an article page that cannot render its graph sidebar is still a
     * perfectly good article page, and letting a traversal problem fail the whole read model would
     * turn a decorative feature into a availability risk.
     */
    private GraphNeighbourhood graphContextFor(Article article) {
        try {
            return graphService.neighbourhoodOf(article.getId(), GRAPH_CONTEXT_DEPTH, true);
        } catch (RuntimeException e) {
            log.warn("Could not build graph context for '{}'; serving the article without it",
                    article.getSlug(), e);
            return GraphNeighbourhood.empty();
        }
    }

    private List<ArticleReadModel.BreadcrumbEntry> breadcrumbsFor(Article article) {
        List<ArticleReadModel.BreadcrumbEntry> trail = new ArrayList<>();
        trail.add(new ArticleReadModel.BreadcrumbEntry("Home", "/"));
        if (article.getPrimaryTopic() != null) {
            trail.add(new ArticleReadModel.BreadcrumbEntry(
                    article.getPrimaryTopic().getName(),
                    "/topics/" + article.getPrimaryTopic().getSlug()));
        }
        trail.add(new ArticleReadModel.BreadcrumbEntry(
                article.getTitle(), "/articles/" + article.getSlug()));
        return trail;
    }

    private ArticleReference toReference(Article article) {
        String authorName = authorService.findById(article.getAuthorId())
                .map(Author::getDisplayName)
                .orElse(null);
        return new ArticleReference(
                article.getSlug(), article.getTitle(), article.getSummary(), authorName);
    }
}

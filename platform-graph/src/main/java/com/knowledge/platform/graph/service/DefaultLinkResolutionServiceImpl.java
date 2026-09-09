package com.knowledge.platform.graph.service;

import com.knowledge.platform.content.model.dto.ExtractedLink;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.graph.model.entity.ArticleLink;
import com.knowledge.platform.graph.model.entity.LinkResolutionState;
import com.knowledge.platform.graph.repository.ArticleLinkRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default LinkResolutionService.
 *
 * <p>See {@link LinkResolutionService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultLinkResolutionServiceImpl implements LinkResolutionService {

    private final ArticleLinkRepository linkRepository;
    private final ArticleService articleService;

    public DefaultLinkResolutionServiceImpl(
            ArticleLinkRepository linkRepository, ArticleService articleService) {
        this.linkRepository = linkRepository;
        this.articleService = articleService;
    }

    @Override
    @Transactional
    public List<String> replaceOutboundLinks(UUID articleId, List<ExtractedLink> extracted) {
        linkRepository.deleteBySourceArticleId(articleId);
        if (extracted.isEmpty()) {
            return List.of();
        }

        // One lookup for every target rather than one per link: an article with twenty references
        // would otherwise cost twenty queries inside the ingestion transaction.
        List<String> targetSlugs = extracted.stream().map(ExtractedLink::targetSlug).distinct().toList();
        Map<String, Article> byslug = articleService.findBySlugs(targetSlugs).stream()
                .collect(Collectors.toMap(Article::getSlug, Function.identity(), (first, second) -> first));

        List<ArticleLink> edges = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (ExtractedLink link : extracted) {
            ArticleLink edge = ArticleLink.from(articleId, link);
            Article target = byslug.get(link.targetSlug());
            if (target != null && !target.getId().equals(articleId)) {
                edge.resolveTo(target.getId());
            } else {
                unresolved.add(link.targetReference());
            }
            edges.add(edge);
        }

        linkRepository.saveAll(edges);
        return unresolved;
    }

    @Override
    @Transactional
    public int resolvePendingLinksTo(Article article) {
        List<ArticleLink> waiting = linkRepository.findByTargetSlugAndResolutionState(
                article.getSlug(), LinkResolutionState.UNRESOLVED);
        if (waiting.isEmpty()) {
            return 0;
        }
        waiting.forEach(link -> link.resolveTo(article.getId()));
        linkRepository.saveAll(waiting);
        log.debug("Resolved {} pending reference(s) to '{}'", waiting.size(), article.getSlug());
        return waiting.size();
    }

    @Override
    @Transactional
    public int unresolveLinksTo(UUID articleId) {
        return linkRepository.unresolveEdgesTargeting(articleId);
    }

    @Override
    public List<ArticleLink> outboundLinks(UUID articleId) {
        return linkRepository.findBySourceArticleId(articleId);
    }

    @Override
    public List<ArticleLink> backlinks(UUID articleId) {
        return linkRepository.findByTargetArticleId(articleId);
    }

    @Override
    public List<ArticleLink> allUnresolved() {
        return linkRepository.findByResolutionState(LinkResolutionState.UNRESOLVED);
    }
}

package com.knowledge.platform.delivery.service;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import com.knowledge.platform.delivery.model.entity.Route;
import com.knowledge.platform.delivery.repository.RouteRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default RouteResolutionService.
 *
 * <p>See {@link RouteResolutionService} for what this provides and why it exists.
 */
@Service
public class DefaultRouteResolutionServiceImpl implements RouteResolutionService {

    private static final String ARTICLE_PREFIX = "/articles/";
    private static final String AUTHOR_PREFIX = "/authors/";
    private static final String TOPIC_PREFIX = "/topics/";
    private static final String TAG_PREFIX = "/tags/";

    private final RouteRepository routes;

    public DefaultRouteResolutionServiceImpl(RouteRepository routes) {
        this.routes = routes;
    }

    @Override
    @Cacheable(cacheNames = "routeResolutions", key = "#path")
    @Transactional(readOnly = true)
    public RouteTarget resolve(String path) {
        return routes.findById(normalize(path))
                .map(Route::toTarget)
                .orElseThrow(() -> NotFoundException.of("Route", path));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "routeResolutions", allEntries = true)
    public void registerArticle(Article article) {
        routes.deleteByTarget(RouteTarget.TargetType.ARTICLE, article.getId());
        if (!article.getPublicationState().isPublic()) {
            // An unpublished article has no public URL. Leaving one would make it reachable.
            return;
        }
        routes.save(new Route(ARTICLE_PREFIX + article.getSlug(),
                RouteTarget.TargetType.ARTICLE, article.getId(), article.getSlug()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "routeResolutions", allEntries = true)
    public void registerAuthor(Author author) {
        routes.deleteByTarget(RouteTarget.TargetType.AUTHOR, author.getId());
        routes.save(new Route(AUTHOR_PREFIX + author.getSlug(),
                RouteTarget.TargetType.AUTHOR, author.getId(), author.getSlug()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "routeResolutions", allEntries = true)
    public void registerTaxonomy(List<Topic> topics, List<Tag> tags) {
        topics.forEach(topic -> {
            routes.deleteByTarget(RouteTarget.TargetType.TOPIC, topic.getId());
            routes.save(new Route(TOPIC_PREFIX + topic.getSlug(),
                    RouteTarget.TargetType.TOPIC, topic.getId(), topic.getSlug()));
        });
        tags.forEach(tag -> {
            routes.deleteByTarget(RouteTarget.TargetType.TAG, tag.getId());
            routes.save(new Route(TAG_PREFIX + tag.getSlug(),
                    RouteTarget.TargetType.TAG, tag.getId(), tag.getSlug()));
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "routeResolutions", allEntries = true)
    public void unregister(RouteTarget.TargetType targetType, UUID targetId) {
        routes.deleteByTarget(targetType, targetId);
    }

    /** Trailing slashes are cosmetic; "/articles/x" and "/articles/x/" are the same page. */
    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

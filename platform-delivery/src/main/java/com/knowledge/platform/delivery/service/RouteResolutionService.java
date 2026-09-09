package com.knowledge.platform.delivery.service;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import java.util.List;
import java.util.UUID;

/**
 * Resolves public URL paths, and keeps the route table in step with content.
 *
 * <p>The backend owns URL structure. That is what lets an article be renamed without the frontend
 * needing to know, and what keeps path conventions from being duplicated in two codebases that then
 * disagree.
 */
public interface RouteResolutionService {

    RouteTarget resolve(String path);

    /**
     * Rewrites an article's routes.
     *
     * <p>Delete-then-insert rather than upsert, because a renamed article's previous path must stop
     * resolving. An upsert keyed on path would leave the old path pointing at the article forever,
     * which is how two URLs end up serving the same content and splitting its search ranking.
     */
    void registerArticle(Article article);

    void registerAuthor(Author author);

    void registerTaxonomy(List<Topic> topics, List<Tag> tags);

    void unregister(RouteTarget.TargetType targetType, UUID targetId);
}

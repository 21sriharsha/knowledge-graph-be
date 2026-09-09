package com.knowledge.platform.delivery.service;

import com.knowledge.platform.content.model.entity.Article;
import java.util.Set;

/**
 * Targeted cache invalidation after content changes.
 *
 * <p>The rule this class exists to honour: never clear the whole cache for a single article change.
 * Publishing one article on a site with thousands would otherwise throw away every cached read model
 * and turn a routine publish into a stampede of re-assembly.
 *
 * <p>What gets invalidated for an article change, and why each entry:
 *
 * <ul>
 *   <li>the article's own read model -- its content changed;
 *   <li>the read models of articles that link to it -- their backlink lists and link resolution
 *       states changed, even though their own content did not;
 *   <li>its author's and topics' pages -- their listings changed;
 *   <li>graph neighbourhoods touching it -- its edges changed.
 * </ul>
 *
 * <p>Graph neighbourhoods are the one place a broader sweep is taken, and deliberately: a
 * neighbourhood's cache key is (slug, depth, suggestions), so the entries containing a given article
 * are not derivable from its id. Keying by every member would mean maintaining a reverse index whose
 * staleness would be a subtler bug than the cost of rebuilding a bounded, cheap cache.
 */
public interface ReadModelCacheInvalidator {

    /**
     * Invalidates everything affected by one article changing.
     *
     * @param linkingArticleSlugs slugs of articles whose links point at this one, from the graph
     *     module. Their read models embed this article's title and resolution state.
     */
    void afterArticleChanged(
            Article article, String authorSlug, Set<String> linkingArticleSlugs);

    /** Invalidates an author's own page, for a profile edit that touches no article. */
    void afterAuthorChanged(String authorSlug);

    /**
     * Invalidates by slug alone, for when the article itself is no longer loadable.
     *
     * <p>Needed when an article is withdrawn and the entity cannot be read back to discover its
     * author and topics — the entry still has to go, or the URL keeps serving from cache.
     */
    void afterArticleSlugChanged(String slug);
}

package com.knowledge.platform.graph.repository;

import com.knowledge.platform.graph.model.entity.ArticleLink;
import com.knowledge.platform.graph.model.entity.LinkResolutionState;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for author-written graph edges. */
public interface ArticleLinkRepository extends JpaRepository<ArticleLink, UUID> {

    /** Outbound edges: what this article points at. */
    List<ArticleLink> findBySourceArticleId(UUID sourceArticleId);

    /** Inbound edges: backlinks, a first-class field of the article read model. */
    List<ArticleLink> findByTargetArticleId(UUID targetArticleId);

    /**
     * Edges waiting for a slug to exist.
     *
     * <p>Queried when an article is published, to repair every reference that was written before its
     * target was. This is what makes the graph self-healing rather than dependent on ingestion order.
     */
    List<ArticleLink> findByTargetSlugAndResolutionState(
            String targetSlug, LinkResolutionState resolutionState);

    List<ArticleLink> findBySourceArticleIdIn(Collection<UUID> sourceArticleIds);

    List<ArticleLink> findByResolutionState(LinkResolutionState resolutionState);

    /**
     * Article ids ordered by how many resolved links point at them.
     *
     * <p>The platform records no views, so there is no popularity signal to rank by. Inbound link
     * count is the signal it genuinely has: how often other authors found an article worth pointing
     * at. Presenting that as "most connected" is truthful, where labelling it "trending" would not
     * be.
     */
    @Query("""
            select l.targetArticleId
              from ArticleLink l
             where l.targetArticleId is not null
             group by l.targetArticleId
             order by count(l) desc
            """)
    List<UUID> findMostLinkedArticleIds(org.springframework.data.domain.Pageable pageable);

    /**
     * Removes an article's outbound edges before its links are rewritten.
     *
     * <p>Replace rather than merge: a link the author deleted must disappear, and diffing old against
     * new is more code and more ways to be wrong than simply rebuilding what is a derived projection
     * of the document anyway.
     */
    @Modifying
    @Query("delete from ArticleLink l where l.sourceArticleId = :articleId")
    int deleteBySourceArticleId(@Param("articleId") UUID articleId);

    /** Breaks inbound edges when a target stops being publicly resolvable. */
    @Modifying
    @Query("""
            update ArticleLink l
               set l.targetArticleId = null,
                   l.resolutionState = com.knowledge.platform.graph.model.entity.LinkResolutionState.UNRESOLVED
             where l.targetArticleId = :articleId
            """)
    int unresolveEdgesTargeting(@Param("articleId") UUID articleId);
}

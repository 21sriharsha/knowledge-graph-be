package com.knowledge.platform.content.repository;

import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.PublicationState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for canonical articles. */
public interface ArticleRepository extends JpaRepository<Article, UUID> {

    /**
     * Fetches an article with its taxonomy in one query.
     *
     * <p>The entity graph exists because the read-model assembler needs tags and topics on every
     * article it builds; without it each assembly costs two extra round trips, which is exactly the
     * N+1 the read-model design is meant to eliminate.
     */
    @EntityGraph(attributePaths = {"tags", "topics", "primaryTopic"})
    Optional<Article> findBySlug(String slug);

    /**
     * Looks an article up by where it came from.
     *
     * <p>This is the lookup ingestion performs first. Using the source coordinate rather than the
     * slug is what makes retitling a file an update instead of a new article plus an orphan.
     */
    Optional<Article> findByRepositoryIdAndSourcePath(UUID repositoryId, String sourcePath);

    boolean existsBySlug(String slug);

    Optional<Article> findByIdAndPublicationState(UUID id, PublicationState publicationState);

    @EntityGraph(attributePaths = {"tags", "topics"})
    Page<Article> findByPublicationStateOrderByPublishedAtDesc(
            PublicationState publicationState, Pageable pageable);

    Page<Article> findByAuthorIdAndPublicationStateOrderByPublishedAtDesc(
            UUID authorId, PublicationState publicationState, Pageable pageable);

    List<Article> findByRepositoryId(UUID repositoryId);

    List<Article> findBySlugIn(Collection<String> slugs);

    @Query("""
            select a from Article a join a.tags t
            where t.slug = :tagSlug and a.publicationState = :state
            order by a.publishedAt desc
            """)
    Page<Article> findByTagSlug(
            @Param("tagSlug") String tagSlug, @Param("state") PublicationState state, Pageable pageable);

    @Query("""
            select a from Article a join a.topics t
            where t.slug = :topicSlug and a.publicationState = :state
            order by a.publishedAt desc
            """)
    Page<Article> findByTopicSlug(
            @Param("topicSlug") String topicSlug, @Param("state") PublicationState state, Pageable pageable);

    /**
     * Articles sharing a tag or topic with the given article, most overlap first.
     *
     * <p>Backs the "related articles" section of the read model. Bounded by the caller's
     * {@link Pageable}, never returning an unbounded set, and it excludes the article itself.
     */
    @Query("""
            select a from Article a
            where a.id <> :articleId
              and a.publicationState = :state
              and (exists (select 1 from Article s join s.tags st join a.tags at
                            where s.id = :articleId and st.id = at.id)
                or exists (select 1 from Article s join s.topics sp join a.topics ap
                            where s.id = :articleId and sp.id = ap.id))
            order by a.publishedAt desc
            """)
    List<Article> findRelated(
            @Param("articleId") UUID articleId, @Param("state") PublicationState state, Pageable pageable);

    /** The article published immediately before this one, for previous/next navigation. */
    Optional<Article> findFirstByPublicationStateAndPublishedAtLessThanOrderByPublishedAtDesc(
            PublicationState state, java.time.Instant publishedAt);

    /** The article published immediately after this one, for previous/next navigation. */
    Optional<Article> findFirstByPublicationStateAndPublishedAtGreaterThanOrderByPublishedAtAsc(
            PublicationState state, java.time.Instant publishedAt);
}

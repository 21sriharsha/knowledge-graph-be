package com.knowledge.platform.author.repository;

import com.knowledge.platform.author.model.entity.Author;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for author identities. */
public interface AuthorRepository extends JpaRepository<Author, UUID> {

    Optional<Author> findBySlug(String slug);

    Optional<Author> findByEmail(String email);

    Page<Author> findAllByOrderByDisplayNameAsc(Pageable pageable);

    /**
     * Resolves an author named in free text, for the search planner's author dimension.
     *
     * <p>Matching is exact on slug and contains-insensitive on display name, because a query names an
     * author the way a human writes them ("Harsh"), not the way a URL spells them ("harsh-c"). The
     * result is a list, not an Optional: two people can share a display name, and it is the planner's
     * job to decide what to do about that, not this query's.
     */
    @Query("""
            select a from Author a
            where a.slug = :slug
               or lower(a.displayName) like lower(concat('%', :name, '%'))
            order by case when a.slug = :slug then 0 else 1 end, a.displayName asc
            """)
    List<Author> findBySlugOrDisplayName(@Param("slug") String slug, @Param("name") String name);
}

package com.knowledge.platform.content.repository;

import com.knowledge.platform.content.model.entity.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for tags. */
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findBySlug(String slug);

    List<Tag> findBySlugIn(Collection<String> slugs);

    List<Tag> findAllByOrderByNameAsc();
}

package com.knowledge.platform.content.repository;

import com.knowledge.platform.content.model.entity.Topic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for topics. */
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findBySlug(String slug);

    List<Topic> findBySlugIn(Collection<String> slugs);

    List<Topic> findAllByOrderByNameAsc();
}

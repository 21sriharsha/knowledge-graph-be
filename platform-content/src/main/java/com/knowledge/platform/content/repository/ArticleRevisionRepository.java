package com.knowledge.platform.content.repository;

import com.knowledge.platform.content.model.entity.ArticleRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for article revision history. */
public interface ArticleRevisionRepository extends JpaRepository<ArticleRevision, UUID> {

    /** The idempotency check: has this exact content already been stored for this article? */
    boolean existsByArticleIdAndContentHash(UUID articleId, String contentHash);

    Optional<ArticleRevision> findFirstByArticleIdOrderByRevisionNumberDesc(UUID articleId);

    List<ArticleRevision> findByArticleIdOrderByRevisionNumberDesc(UUID articleId);
}

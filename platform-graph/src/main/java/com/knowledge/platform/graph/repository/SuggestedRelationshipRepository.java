package com.knowledge.platform.graph.repository;

import com.knowledge.platform.graph.model.entity.SuggestedRelationship;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for platform- and AI-suggested edges. */
public interface SuggestedRelationshipRepository extends JpaRepository<SuggestedRelationship, UUID> {

    List<SuggestedRelationship> findBySourceArticleIdOrderByScoreDesc(
            UUID sourceArticleId, Pageable pageable);

    @Modifying
    @Query("delete from SuggestedRelationship s where s.sourceArticleId = :articleId "
            + "or s.targetArticleId = :articleId")
    int deleteInvolving(@Param("articleId") UUID articleId);
}

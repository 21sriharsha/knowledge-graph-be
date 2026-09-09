package com.knowledge.platform.delivery.repository;

import com.knowledge.platform.delivery.model.entity.ArticleReadModelRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for materialized article read models. */
public interface ArticleReadModelRepository extends JpaRepository<ArticleReadModelRecord, UUID> {
}

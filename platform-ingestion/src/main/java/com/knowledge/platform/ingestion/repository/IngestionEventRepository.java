package com.knowledge.platform.ingestion.repository;

import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for ingestion diagnostics. */
public interface IngestionEventRepository extends JpaRepository<IngestionEvent, UUID> {

    List<IngestionEvent> findByRunIdOrderByOccurredAtAsc(UUID runId);
}

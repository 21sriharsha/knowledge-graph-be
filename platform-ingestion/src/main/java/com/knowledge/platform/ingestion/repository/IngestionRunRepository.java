package com.knowledge.platform.ingestion.repository;

import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for ingestion runs. */
public interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> {

    Optional<IngestionRun> findByCorrelationId(String correlationId);

    List<IngestionRun> findByRepositoryIdOrderByStartedAtDesc(UUID repositoryId, Pageable pageable);

    List<IngestionRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}

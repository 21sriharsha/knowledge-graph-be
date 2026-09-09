package com.knowledge.platform.source.repository;

import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for connected source repositories. */
public interface SourceRepositoryRepository extends JpaRepository<SourceRepository, UUID> {

    List<SourceRepository> findByActiveTrue();

    Optional<SourceRepository> findBySourceTypeAndOwnerAndRepository(
            SourceType sourceType, String owner, String repository);
}

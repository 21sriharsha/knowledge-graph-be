package com.knowledge.platform.source.repository;

import com.knowledge.platform.source.model.entity.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for the webhook delivery ledger that makes retried deliveries idempotent. */
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    boolean existsByRepositoryIdAndDeliveryId(UUID repositoryId, String deliveryId);

    /**
     * Prunes the ledger.
     *
     * <p>It only has to remember long enough to outlast a provider's retry window, which is measured
     * in hours. Keeping every delivery forever would grow a table nothing ever reads.
     */
    @Modifying
    @Query("delete from WebhookDelivery d where d.receivedAt < :before")
    int deleteReceivedBefore(@Param("before") Instant before);
}

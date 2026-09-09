package com.knowledge.platform.source.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A provider webhook delivery that has already been accepted.
 *
 * <p>The unique constraint on {@code (repository_id, delivery_id)} is the idempotency mechanism.
 * Providers retry deliveries they believe failed, and without this a retry would re-run the whole
 * materialization pipeline.
 */
@Entity
@Table(name = "webhook_deliveries", schema = "source")
public class WebhookDelivery {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repository_id", nullable = false, updatable = false)
    private UUID repositoryId;

    @Column(name = "delivery_id", nullable = false, length = 200, updatable = false)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID repositoryId, String deliveryId, String eventType) {
        this.id = UUID.randomUUID();
        this.repositoryId = repositoryId;
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.receivedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}

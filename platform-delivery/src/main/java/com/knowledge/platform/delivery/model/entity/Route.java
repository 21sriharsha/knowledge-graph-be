package com.knowledge.platform.delivery.model.entity;

import com.knowledge.platform.delivery.model.dto.RouteTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A materialized public URL path.
 *
 * <p>Exists so route resolution is one indexed primary-key read rather than a sequence of guesses --
 * try the article repository, then authors, then topics, then tags -- which is four queries to
 * discover that a path is a 404.
 */
@Entity
@Table(name = "routes", schema = "read_model")
public class Route {

    @Id
    @Column(name = "path", nullable = false, updatable = false, length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private RouteTarget.TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_slug", nullable = false, length = 160)
    private String targetSlug;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Route() {
    }

    public Route(String path, RouteTarget.TargetType targetType, UUID targetId, String targetSlug) {
        this.path = path;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetSlug = targetSlug;
        this.updatedAt = Instant.now();
    }

    public void retarget(UUID targetId, String targetSlug) {
        this.targetId = targetId;
        this.targetSlug = targetSlug;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getPath() {
        return path;
    }

    public RouteTarget.TargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetSlug() {
        return targetSlug;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public RouteTarget toTarget() {
        return new RouteTarget(targetType, targetId, targetSlug);
    }
}

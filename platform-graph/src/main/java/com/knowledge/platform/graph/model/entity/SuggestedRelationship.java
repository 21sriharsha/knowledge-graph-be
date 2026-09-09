package com.knowledge.platform.graph.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A platform- or AI-proposed edge.
 *
 * <p>A separate entity and a separate table from {@link ArticleLink}, not a variant of it. A
 * suggestion must never be capable of being mistaken for something an author wrote, and separating
 * them at the storage level means a query over authored links cannot accidentally include
 * suggestions -- which a shared table with a discriminator column would allow on any forgotten
 * {@code WHERE} clause.
 */
@Entity
@Table(name = "suggested_relationships", schema = "graph")
public class SuggestedRelationship {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_article_id", nullable = false, updatable = false)
    private UUID sourceArticleId;

    @Column(name = "target_article_id", nullable = false, updatable = false)
    private UUID targetArticleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_kind", nullable = false, length = 32)
    private RelationshipKind relationshipKind;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "generated_by", nullable = false, length = 64)
    private String generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SuggestedRelationship() {
    }

    public SuggestedRelationship(UUID sourceArticleId, UUID targetArticleId,
            RelationshipKind relationshipKind, double score, String generatedBy) {
        if (sourceArticleId.equals(targetArticleId)) {
            throw new IllegalArgumentException("An article cannot be related to itself");
        }
        this.id = UUID.randomUUID();
        this.sourceArticleId = sourceArticleId;
        this.targetArticleId = targetArticleId;
        this.relationshipKind = relationshipKind;
        this.score = Math.clamp(score, 0.0, 1.0);
        this.generatedBy = generatedBy;
        this.createdAt = Instant.now();
    }

    /** How a suggestion was arrived at. Kept so a reader can be told why two articles are linked. */
    public enum RelationshipKind {
        SHARED_TOPIC,
        SHARED_TAG,
        SEMANTIC_SIMILARITY
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceArticleId() {
        return sourceArticleId;
    }

    public UUID getTargetArticleId() {
        return targetArticleId;
    }

    public RelationshipKind getRelationshipKind() {
        return relationshipKind;
    }

    public double getScore() {
        return score;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SuggestedRelationship relationship
                && id != null && id.equals(relationship.id);
    }

    @Override
    public int hashCode() {
        return SuggestedRelationship.class.hashCode();
    }
}

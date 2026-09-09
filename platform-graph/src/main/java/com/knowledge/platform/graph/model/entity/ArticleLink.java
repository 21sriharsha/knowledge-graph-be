package com.knowledge.platform.graph.model.entity;

import com.knowledge.platform.content.model.dto.ExtractedLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An author-written edge between two articles.
 *
 * <p>Authoritative in the way suggestions are not: this records what somebody actually wrote. Both
 * ends are stored -- the normalized {@code targetSlug} always, and {@code targetArticleId} only once
 * a matching article exists -- so an unresolved edge can be repaired by slug when its target appears,
 * without re-parsing the source document.
 */
@Entity
@Table(name = "article_links", schema = "graph")
public class ArticleLink {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_article_id", nullable = false, updatable = false)
    private UUID sourceArticleId;

    @Column(name = "target_article_id")
    private UUID targetArticleId;

    @Column(name = "target_reference", nullable = false, length = 500)
    private String targetReference;

    @Column(name = "target_slug", nullable = false, length = 160)
    private String targetSlug;

    @Column(name = "display_text", length = 500)
    private String displayText;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 32)
    private ExtractedLink.LinkType linkType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_state", nullable = false, length = 32)
    private LinkResolutionState resolutionState;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArticleLink() {
    }

    private ArticleLink(UUID sourceArticleId, ExtractedLink link) {
        this.id = UUID.randomUUID();
        this.sourceArticleId = sourceArticleId;
        this.targetReference = link.targetReference();
        this.targetSlug = link.targetSlug();
        this.displayText = link.displayText();
        this.linkType = link.type();
        this.ordinal = link.ordinal();
        this.resolutionState = LinkResolutionState.UNRESOLVED;
        this.createdAt = Instant.now();
    }

    public static ArticleLink from(UUID sourceArticleId, ExtractedLink link) {
        return new ArticleLink(sourceArticleId, link);
    }

    /** Binds an unresolved edge to a target that now exists. */
    public void resolveTo(UUID targetArticleId) {
        this.targetArticleId = targetArticleId;
        this.resolutionState = LinkResolutionState.RESOLVED;
    }

    /**
     * Breaks the binding when the target is deleted or unpublished.
     *
     * <p>The edge survives as UNRESOLVED rather than being removed, so republishing the target
     * restores the link without re-ingesting every article that pointed at it.
     */
    public void unresolve() {
        this.targetArticleId = null;
        this.resolutionState = LinkResolutionState.UNRESOLVED;
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

    public String getTargetReference() {
        return targetReference;
    }

    public String getTargetSlug() {
        return targetSlug;
    }

    public String getDisplayText() {
        return displayText;
    }

    public ExtractedLink.LinkType getLinkType() {
        return linkType;
    }

    public LinkResolutionState getResolutionState() {
        return resolutionState;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isResolved() {
        return resolutionState == LinkResolutionState.RESOLVED;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArticleLink link && id != null && id.equals(link.id);
    }

    @Override
    public int hashCode() {
        return ArticleLink.class.hashCode();
    }
}

package com.knowledge.platform.content.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored revision of an article's canonical Markdown.
 *
 * <p>Append-only. The unique constraint on {@code (article_id, content_hash)} means a replayed
 * ingestion cannot create a duplicate row even if the pipeline's own guard is bypassed.
 */
@Entity
@Table(name = "article_revisions", schema = "content")
public class ArticleRevision {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "article_id", nullable = false, updatable = false)
    private UUID articleId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "markdown", nullable = false, columnDefinition = "text")
    private String markdown;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "source_revision", length = 200)
    private String sourceRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArticleRevision() {
    }

    private ArticleRevision(UUID id, UUID articleId, int revisionNumber, String title,
            String markdown, String contentHash, String sourceRevision) {
        this.id = Objects.requireNonNull(id, "id");
        this.articleId = Objects.requireNonNull(articleId, "articleId");
        this.revisionNumber = revisionNumber;
        this.title = Objects.requireNonNull(title, "title");
        this.markdown = Objects.requireNonNull(markdown, "markdown");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.sourceRevision = sourceRevision;
        this.createdAt = Instant.now();
    }

    public static ArticleRevision of(Article article) {
        return new ArticleRevision(
                UUID.randomUUID(),
                article.getId(),
                article.getRevisionNumber(),
                article.getTitle(),
                article.getCanonicalMarkdown(),
                article.getContentHash(),
                article.getSourceRevision());
    }

    public UUID getId() {
        return id;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArticleRevision revision && id != null && id.equals(revision.id);
    }

    @Override
    public int hashCode() {
        return ArticleRevision.class.hashCode();
    }
}

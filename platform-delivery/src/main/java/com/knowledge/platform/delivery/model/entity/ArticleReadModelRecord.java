package com.knowledge.platform.delivery.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A persisted article read model.
 *
 * <p>The payload is stored as JSON rather than as columns, because its shape is a frontend contract
 * that will evolve and the backend never queries into it -- it is always fetched whole by primary
 * key. Storing it any other way would mean a migration every time the page gains a field.
 *
 * <p>Two independent staleness questions are answered here. {@code contentHash} records which
 * revision of the article the payload was built from. {@code schemaVersion} records which payload
 * shape the assembler produced -- because the read model can change without the article changing,
 * and a hash-current payload of the previous shape would otherwise be served indefinitely, silently
 * missing whatever field was added.
 */
@Entity
@Table(name = "article_read_models", schema = "read_model")
public class ArticleReadModelRecord {

    @Id
    @Column(name = "article_id", nullable = false, updatable = false)
    private UUID articleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ArticleReadModelRecord() {
    }

    public ArticleReadModelRecord(
            UUID articleId, String payload, String contentHash, int schemaVersion) {
        this.articleId = articleId;
        this.payload = payload;
        this.contentHash = contentHash;
        this.schemaVersion = schemaVersion;
        this.generatedAt = Instant.now();
    }

    public void replace(String payload, String contentHash, int schemaVersion) {
        this.payload = payload;
        this.contentHash = contentHash;
        this.schemaVersion = schemaVersion;
        this.generatedAt = Instant.now();
    }

    public UUID getArticleId() {
        return articleId;
    }

    public String getPayload() {
        return payload;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /** Usable as-is: built from this revision of the article, by this version of the assembler. */
    public boolean matches(String contentHash, int schemaVersion) {
        return this.schemaVersion == schemaVersion && this.contentHash.equals(contentHash);
    }
}

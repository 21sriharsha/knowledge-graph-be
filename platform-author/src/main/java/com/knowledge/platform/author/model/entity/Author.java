package com.knowledge.platform.author.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An author identity.
 *
 * <p>The author module owns who someone is and how they are presented. It deliberately holds no
 * collection of articles: content ownership belongs to the content module, and modelling the
 * association only from the article side keeps two modules from silently becoming one aggregate
 * whose lazy collection nobody can safely load.
 */
@Entity
@Table(name = "authors", schema = "author")
public class Author {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 160)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "biography", columnDefinition = "text")
    private String biography;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Author() {
        // required by JPA
    }

    private Author(UUID id, String slug, String displayName) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * The id is generated here rather than by the database, so that callers hold the identity before
     * the row exists -- which ingestion needs in order to build links and diagnostics for content it
     * has not yet flushed.
     */
    public static Author create(String slug, String displayName) {
        return new Author(UUID.randomUUID(), slug, displayName);
    }

    public void updateProfile(String displayName, String biography, String avatarUrl, String email) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.biography = biography;
        this.avatarUrl = avatarUrl;
        this.email = email;
    }

    /**
     * Keeps {@code updated_at} honest. The column's database DEFAULT only fires on INSERT, so without
     * this callback the value would silently stay at its creation time forever.
     */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getBiography() {
        return biography;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Author author && id != null && id.equals(author.id);
    }

    @Override
    public int hashCode() {
        // Constant, not Objects.hash(id): a JPA entity's id can be assigned after it has been put in
        // a HashSet, and a changing hash code would lose it there.
        return Author.class.hashCode();
    }
}

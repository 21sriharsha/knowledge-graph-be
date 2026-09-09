package com.knowledge.platform.content.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The canonical article.
 *
 * <p>This is the source of truth. Search documents, embeddings, graph edges and read models are all
 * derived from it and can be rebuilt from {@link #getCanonicalMarkdown()} alone.
 *
 * <p><b>Cross-module references are ids, not associations.</b> {@code authorId} and
 * {@code repositoryId} are plain UUIDs rather than {@code @ManyToOne} mappings, even though this
 * module can see both classes. Mapping them would put three modules into one Hibernate persistence
 * graph, where a lazy proxy resolved in the wrong transaction becomes another module's problem, and
 * would make it possible to write to an author through an article. Within the content module --
 * tags, topics, revisions -- associations are used freely, because that is one aggregate.
 */
@Entity
@Table(name = "articles", schema = "content")
public class Article {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 160)
    private String slug;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "canonical_markdown", nullable = false, columnDefinition = "text")
    private String canonicalMarkdown;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false, length = 32)
    private PublicationState publicationState;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_topic_id")
    private Topic primaryTopic;

    @Column(name = "repository_id")
    private UUID repositoryId;

    @Column(name = "source_path", length = 1000)
    private String sourcePath;

    @Column(name = "source_revision", length = 200)
    private String sourceRevision;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "reading_time_minutes", nullable = false)
    private int readingTimeMinutes;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "article_tags", schema = "content",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "article_topics", schema = "content",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id"))
    private Set<Topic> topics = new LinkedHashSet<>();

    protected Article() {
    }

    private Article(UUID id, String slug, String title, String summary, String canonicalMarkdown,
            String contentHash, UUID authorId, int wordCount, int readingTimeMinutes) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.title = Objects.requireNonNull(title, "title");
        this.summary = summary;
        this.canonicalMarkdown = Objects.requireNonNull(canonicalMarkdown, "canonicalMarkdown");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.wordCount = wordCount;
        this.readingTimeMinutes = readingTimeMinutes;
        this.publicationState = PublicationState.DRAFT;
        this.revisionNumber = 1;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates an article complete with its derived counts.
     *
     * <p>Everything is supplied here rather than by a follow-up call to
     * {@link #applyRevision}: that method short-circuits when the hash it is given already matches,
     * so on a new article it would correctly decide nothing had changed and leave summary, word count
     * and reading time unset.
     */
    public static Article create(String slug, String title, String summary, String canonicalMarkdown,
            String contentHash, UUID authorId, int wordCount, int readingTimeMinutes) {
        return new Article(UUID.randomUUID(), slug, title, summary, canonicalMarkdown, contentHash,
                authorId, wordCount, readingTimeMinutes);
    }

    /**
     * Applies a new revision of the source content.
     *
     * <p>Returns whether anything changed. Ingestion uses that answer to skip the whole
     * materialization pipeline for a document whose bytes are identical to what is already stored,
     * which is what makes a replayed webhook cheap rather than merely harmless.
     */
    public boolean applyRevision(
            String title, String summary, String canonicalMarkdown, String contentHash,
            String sourceRevision, int wordCount, int readingTimeMinutes) {
        if (this.contentHash.equals(contentHash)) {
            // The revision pointer may still move when a commit touches other files, and recording it
            // keeps "which revision have we seen" accurate without rewriting derived state.
            this.sourceRevision = sourceRevision;
            return false;
        }
        this.title = Objects.requireNonNull(title, "title");
        this.summary = summary;
        this.canonicalMarkdown = Objects.requireNonNull(canonicalMarkdown, "canonicalMarkdown");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.sourceRevision = sourceRevision;
        this.wordCount = wordCount;
        this.readingTimeMinutes = readingTimeMinutes;
        this.revisionNumber++;
        return true;
    }

    /** Records where this article came from, so re-ingestion can find it by coordinate. */
    public void bindToSource(UUID repositoryId, String sourcePath) {
        this.repositoryId = repositoryId;
        this.sourcePath = sourcePath;
    }

    /** Notes which provider revision this content came from. */
    public void recordSourceRevision(String sourceRevision) {
        this.sourceRevision = sourceRevision;
    }

    public void publish() {
        this.publicationState = PublicationState.PUBLISHED;
        // Set once and never moved: publishedAt is the article's first appearance, and re-publishing
        // after an edit must not reorder it to the top of a chronological listing.
        if (this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    public void unpublish() {
        this.publicationState = PublicationState.DRAFT;
    }

    public void archive() {
        this.publicationState = PublicationState.ARCHIVED;
    }

    /** Renaming is allowed; the source coordinate, not the slug, is the article's stable identity. */
    public void rename(String slug) {
        this.slug = Objects.requireNonNull(slug, "slug");
    }

    public void reassignAuthor(UUID authorId) {
        this.authorId = Objects.requireNonNull(authorId, "authorId");
    }

    public void replaceTags(Set<Tag> replacement) {
        this.tags.clear();
        this.tags.addAll(replacement);
    }

    public void replaceTopics(Set<Topic> replacement) {
        this.topics.clear();
        this.topics.addAll(replacement);
        // The primary topic is the first one the author listed; ordering is meaningful in frontmatter.
        this.primaryTopic = replacement.stream().findFirst().orElse(null);
    }

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

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getCanonicalMarkdown() {
        return canonicalMarkdown;
    }

    public String getContentHash() {
        return contentHash;
    }

    public PublicationState getPublicationState() {
        return publicationState;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public Topic getPrimaryTopic() {
        return primaryTopic;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public int getWordCount() {
        return wordCount;
    }

    public int getReadingTimeMinutes() {
        return readingTimeMinutes;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Tag> getTags() {
        return Set.copyOf(tags);
    }

    public Set<Topic> getTopics() {
        return Set.copyOf(topics);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Article article && id != null && id.equals(article.id);
    }

    @Override
    public int hashCode() {
        return Article.class.hashCode();
    }
}

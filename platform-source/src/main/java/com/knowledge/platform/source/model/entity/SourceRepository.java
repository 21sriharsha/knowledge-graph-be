package com.knowledge.platform.source.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A connected external repository.
 *
 * <p>Credentials are held only as ciphertext. The entity exposes no getter that returns a usable
 * token: {@link com.knowledge.platform.source.service.SourceCredentialCipher} is the only thing that
 * can decrypt one, and it does so into a transient
 * {@link com.knowledge.platform.source.model.dto.RepositoryDescriptor}. That means a stray
 * {@code toString()}, a JSON serialization of this entity, or an actuator dump cannot leak a token.
 */
@Entity
@Table(name = "repositories", schema = "source")
public class SourceRepository {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "owner", nullable = false, length = 200)
    private String owner;

    @Column(name = "repository", nullable = false, length = 200)
    private String repository;

    @Column(name = "project", length = 200)
    private String project;

    @Column(name = "default_branch", nullable = false, length = 200)
    private String defaultBranch;

    @Column(name = "content_path", nullable = false, length = 500)
    private String contentPath;

    @Column(name = "api_base_url", length = 500)
    private String apiBaseUrl;

    @Column(name = "access_token_ciphertext", columnDefinition = "text")
    private String accessTokenCiphertext;

    @Column(name = "webhook_secret_ciphertext", columnDefinition = "text")
    private String webhookSecretCiphertext;

    @Column(name = "owner_author_id")
    private UUID ownerAuthorId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_synced_revision", length = 200)
    private String lastSyncedRevision;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SourceRepository() {
    }

    private SourceRepository(UUID id, SourceType sourceType, String displayName, String owner,
            String repository, String project, String defaultBranch, String contentPath) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.project = project;
        this.defaultBranch = defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch;
        this.contentPath = contentPath == null ? "" : normalizePath(contentPath);
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SourceRepository create(SourceType sourceType, String displayName, String owner,
            String repository, String project, String defaultBranch, String contentPath) {
        if (sourceType.requiresProject() && (project == null || project.isBlank())) {
            throw new IllegalArgumentException(sourceType + " requires a project");
        }
        return new SourceRepository(UUID.randomUUID(), sourceType, displayName, owner, repository,
                project, defaultBranch, contentPath);
    }

    /** Leading and trailing slashes are noise; storing one form keeps path prefix checks simple. */
    private static String normalizePath(String path) {
        String trimmed = path.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public void storeCredentials(String accessTokenCiphertext, String webhookSecretCiphertext) {
        if (accessTokenCiphertext != null) {
            this.accessTokenCiphertext = accessTokenCiphertext;
        }
        if (webhookSecretCiphertext != null) {
            this.webhookSecretCiphertext = webhookSecretCiphertext;
        }
    }

    public void updateSettings(String displayName, String defaultBranch, String contentPath,
            String apiBaseUrl, UUID ownerAuthorId) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.defaultBranch = defaultBranch == null || defaultBranch.isBlank() ? this.defaultBranch : defaultBranch;
        this.contentPath = contentPath == null ? this.contentPath : normalizePath(contentPath);
        this.apiBaseUrl = apiBaseUrl;
        this.ownerAuthorId = ownerAuthorId;
    }

    public void recordSync(String revision) {
        this.lastSyncedRevision = revision;
        this.lastSyncedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Whether a path in this repository is content this connection is responsible for. */
    public boolean coversPath(String path) {
        return contentPath.isEmpty() || path.startsWith(contentPath + "/") || path.equals(contentPath);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public String getProject() {
        return project;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getContentPath() {
        return contentPath;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public UUID getOwnerAuthorId() {
        return ownerAuthorId;
    }

    public boolean isActive() {
        return active;
    }

    public String getLastSyncedRevision() {
        return lastSyncedRevision;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Ciphertext accessors, package-visible in intent: only the credential cipher should call these.
     * They are public because the service that composes them lives in a sibling package, but they
     * return ciphertext, so a caller that misuses them gets something unusable rather than a secret.
     */
    public String getAccessTokenCiphertext() {
        return accessTokenCiphertext;
    }

    public String getWebhookSecretCiphertext() {
        return webhookSecretCiphertext;
    }

    public boolean hasWebhookSecret() {
        return webhookSecretCiphertext != null && !webhookSecretCiphertext.isBlank();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SourceRepository repo && id != null && id.equals(repo.id);
    }

    @Override
    public int hashCode() {
        return SourceRepository.class.hashCode();
    }

    /** Never includes credentials, deliberately. */
    @Override
    public String toString() {
        return "SourceRepository[" + sourceType + " " + owner + "/" + repository + "]";
    }
}

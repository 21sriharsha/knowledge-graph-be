package com.knowledge.platform.author.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Someone who can sign in.
 *
 * <p>Distinct from {@link Author}, and the distinction is the point. An author is a byline, created
 * implicitly during ingestion from whatever a Markdown file claimed, and belonging to people who may
 * never visit the site. An account is a person who proved control of an identity at an OIDC
 * provider. Linking the two is an administrative act, because an unverified name in a committed file
 * must never be able to claim a sign-in identity.
 *
 * <p>An account with no roles is the normal initial state: it can sign in and do nothing at all.
 * That is deliberate -- a new sign-in should grant nothing until someone decides otherwise.
 */
@Entity
@Table(name = "accounts", schema = "author")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "issuer", nullable = false, length = 500, updatable = false)
    private String issuer;

    @Column(name = "subject", nullable = false, length = 255, updatable = false)
    private String subject;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "author_id")
    private UUID authorId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", schema = "author",
            joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<PlatformRole> roles = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected Account() {
    }

    private Account(UUID id, String issuer, String subject) {
        this.id = id;
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** A newly seen identity: no roles, no linked author. */
    public static Account of(String issuer, String subject, String email, String displayName) {
        Account account = new Account(UUID.randomUUID(), issuer, subject);
        account.email = email;
        account.displayName = displayName;
        return account;
    }

    /**
     * Refreshes the details copied from the token.
     *
     * <p>Display fields only. Nothing here can change what the account may do, so a provider that
     * changes an email address cannot change a permission.
     */
    public void refreshFromToken(String email, String displayName) {
        this.email = email;
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        this.lastSeenAt = Instant.now();
    }

    public void grant(PlatformRole role) {
        roles.add(role);
    }

    public void revoke(PlatformRole role) {
        roles.remove(role);
    }

    /** Links this account to the byline it writes under. */
    public void linkAuthor(UUID authorId) {
        this.authorId = authorId;
    }

    public void unlinkAuthor() {
        this.authorId = null;
    }

    public UUID getId() {
        return id;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public Set<PlatformRole> getRoles() {
        return Set.copyOf(roles);
    }

    public boolean hasRole(PlatformRole role) {
        return roles.contains(role);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

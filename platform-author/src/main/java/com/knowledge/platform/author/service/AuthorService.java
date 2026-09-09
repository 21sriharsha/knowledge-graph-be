package com.knowledge.platform.author.service;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.common.model.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The author module's application boundary.
 *
 * <p>Every other module -- ingestion resolving a frontmatter author, delivery assembling a profile
 * page, search resolving an author filter -- comes through here rather than through the repository,
 * so the author module keeps control of its own invariants and of how identities come into being.
 */
public interface AuthorService {

    Author requireBySlug(Slug slug);

    Author requireById(UUID id);

    Optional<Author> findBySlug(Slug slug);

    Optional<Author> findById(UUID id);

    Page<Author> findAll(Pageable pageable);

    /**
     * Resolves an author named in free text, as it appears in Markdown frontmatter or in a
     * natural-language query.
     *
     * @return every plausible match, most confident first; empty when the name resolves to nobody
     */
    List<Author> resolveByName(String name);

    /**
     * Returns the author for an ingested document, creating the identity on first sight.
     *
     * <p>Ingestion must not fail because an author record does not exist yet: the Markdown is the
     * source of truth for who wrote a document, and requiring an operator to pre-create profiles
     * would make connecting a repository a two-step manual process. The created profile is a stub a
     * human can enrich later.
     *
     * <p>Email is checked as a second identity key because the same person can appear as "Harsh" in
     * one file's frontmatter and "Harsh C" in another; matching on the address they commit with
     * keeps that one identity rather than two.
     */
    Author findOrCreateByName(String displayName, String email);

    Author updateProfile(
            Slug slug, String displayName, String biography, String avatarUrl, String email);
}

package com.knowledge.platform.author.service;

import com.knowledge.platform.author.model.dto.AuthorProfileChangedEvent;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.repository.AuthorRepository;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.common.service.SlugPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default AuthorService.
 *
 * <p>See {@link AuthorService} for what this provides and why it exists.
 */
@Service
@Transactional(readOnly = true)
public class DefaultAuthorServiceImpl implements AuthorService {

    /** Fallback when a name yields no slug at all (e.g. only punctuation or non-Latin script). */
    private static final String FALLBACK_SLUG = "author";
    /** Leaves room for a numeric suffix within the 160-character slug column. */
    private static final int MAX_BASE_SLUG_LENGTH = 150;
    private static final int MAX_DISPLAY_NAME_LENGTH = 200;

    private final AuthorRepository authorRepository;
    private final SlugPolicy slugPolicy;
    private final ApplicationEventPublisher events;

    public DefaultAuthorServiceImpl(
            AuthorRepository authorRepository, SlugPolicy slugPolicy, ApplicationEventPublisher events) {
        this.authorRepository = authorRepository;
        this.slugPolicy = slugPolicy;
        this.events = events;
    }

    @Override
    public Author requireBySlug(Slug slug) {
        return authorRepository.findBySlug(slug.value())
                .orElseThrow(() -> NotFoundException.of("Author", slug.value()));
    }

    @Override
    public Author requireById(UUID id) {
        return authorRepository.findById(id).orElseThrow(() -> NotFoundException.of("Author", id));
    }

    @Override
    public Optional<Author> findBySlug(Slug slug) {
        return authorRepository.findBySlug(slug.value());
    }

    @Override
    public Optional<Author> findById(UUID id) {
        return authorRepository.findById(id);
    }

    @Override
    public Page<Author> findAll(Pageable pageable) {
        return authorRepository.findAllByOrderByDisplayNameAsc(pageable);
    }

    @Override
    public List<Author> resolveByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        Slug candidate = slugPolicy.slugifyOrNull(name);
        return authorRepository.findBySlugOrDisplayName(candidate == null ? "" : candidate.value(), name);
    }

    @Override
    @Transactional
    public Author findOrCreateByName(String displayName, String email) {
        Slug slug = slugPolicy.slugify(displayName);
        return authorRepository.findBySlug(slug.value())
                .or(() -> email == null || email.isBlank()
                        ? Optional.empty()
                        : authorRepository.findByEmail(email))
                .orElseGet(() -> {
                    Author author = Author.create(slug.value(), displayName);
                    author.updateProfile(displayName, null, null, email);
                    return authorRepository.save(author);
                });
    }

    @Override
    @Transactional
    public Author updateProfile(
            Slug slug, String displayName, String biography, String avatarUrl, String email) {
        Author author = requireBySlug(slug);
        author.updateProfile(displayName, biography, avatarUrl, email);
        Author saved = authorRepository.save(author);
        // Read models embed the byline's name; delivery rebuilds them after this commits.
        events.publishEvent(new AuthorProfileChangedEvent(saved.getId(), saved.getSlug()));
        return saved;
    }

    @Override
    @Transactional
    public Author createPersonal(String displayName) {
        String name = displayName.strip();
        if (name.length() > MAX_DISPLAY_NAME_LENGTH) {
            name = name.substring(0, MAX_DISPLAY_NAME_LENGTH).strip();
        }
        Slug base = slugPolicy.slugifyOrNull(name);
        String baseSlug = base == null ? FALLBACK_SLUG : base.value();
        if (baseSlug.length() > MAX_BASE_SLUG_LENGTH) {
            baseSlug = baseSlug.substring(0, MAX_BASE_SLUG_LENGTH);
        }
        // Check-then-insert: two different people choosing the same name in the same instant can
        // still collide on the unique slug. That request fails once and the next one succeeds,
        // which is cheaper than a retry loop for an event this rare.
        String slug = baseSlug;
        for (int suffix = 2; authorRepository.findBySlug(slug).isPresent(); suffix++) {
            slug = baseSlug + "-" + suffix;
        }
        return authorRepository.save(Author.create(slug, name));
    }
}

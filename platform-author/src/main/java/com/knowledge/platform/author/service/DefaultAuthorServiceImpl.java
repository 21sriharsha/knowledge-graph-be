package com.knowledge.platform.author.service;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.repository.AuthorRepository;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.common.service.SlugPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private final AuthorRepository authorRepository;
    private final SlugPolicy slugPolicy;

    public DefaultAuthorServiceImpl(AuthorRepository authorRepository, SlugPolicy slugPolicy) {
        this.authorRepository = authorRepository;
        this.slugPolicy = slugPolicy;
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
        return authorRepository.save(author);
    }
}

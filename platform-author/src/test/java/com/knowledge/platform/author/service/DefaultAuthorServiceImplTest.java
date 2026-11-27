package com.knowledge.platform.author.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.dto.AuthorProfileChangedEvent;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.repository.AuthorRepository;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.common.service.DefaultSlugPolicyImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Personal byline creation and profile-change events (self-serve authoring, AC-6, AC-9). */
class DefaultAuthorServiceImplTest {

    private final AuthorRepository authors = mock(AuthorRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DefaultAuthorServiceImpl service =
            new DefaultAuthorServiceImpl(authors, new DefaultSlugPolicyImpl(), events);

    @BeforeEach
    void saveReturnsArgument() {
        when(authors.save(any(Author.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authors.findBySlug(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("AC-6 a new byline never reuses an existing author: a taken slug gets a suffix")
    void suffixesTakenSlug() {
        when(authors.findBySlug("anita-rao")).thenReturn(Optional.of(Author.create("anita-rao", "Anita Rao")));
        when(authors.findBySlug("anita-rao-2")).thenReturn(Optional.of(Author.create("anita-rao-2", "Anita Rao")));

        Author created = service.createPersonal("  Anita Rao ");

        assertThat(created.getSlug()).isEqualTo("anita-rao-3");
        assertThat(created.getDisplayName()).isEqualTo("Anita Rao");
        assertThat(created.getEmail()).isNull(); // never matchable by a front-matter author_email
        verify(authors, never()).findByEmail(any());
    }

    @Test
    @DisplayName("a name with no usable slug characters falls back to 'author'")
    void slugFallback() {
        assertThat(service.createPersonal("株式会社").getSlug()).isEqualTo("author");
    }

    @Test
    @DisplayName("AC-9 a profile update announces the change")
    void updatePublishesEvent() {
        Author existing = Author.create("jane-doe", "Jane Doe");
        when(authors.findBySlug("jane-doe")).thenReturn(Optional.of(existing));

        service.updateProfile(Slug.of("jane-doe"), "Jane D.", null, null, null);

        ArgumentCaptor<AuthorProfileChangedEvent> event = ArgumentCaptor.forClass(AuthorProfileChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new AuthorProfileChangedEvent(existing.getId(), "jane-doe"));
    }
}

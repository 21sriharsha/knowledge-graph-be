package com.knowledge.platform.author.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.repository.AccountRepository;
import com.knowledge.platform.author.repository.AuthorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Personal bylines (self-serve authoring, AC-6, AC-7). */
class DefaultAccountServiceImplPersonalAuthorTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthorRepository authors = mock(AuthorRepository.class);
    private final AuthorService authorService = mock(AuthorService.class);
    private final DefaultAccountServiceImpl service = new DefaultAccountServiceImpl(
            accounts, authors, authorService, new AccountSecurityProperties(List.of()));

    private Account stored(String email, String displayName) {
        Account account = Account.of("https://issuer.example", "subject-1", email, displayName);
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    private Author byline(String slug, String name) {
        Author author = Author.create(slug, name);
        when(authorService.createPersonal(any())).thenReturn(author);
        return author;
    }

    @Test
    @DisplayName("AC-6 creates a byline named after the account and links it")
    void createsAndLinks() {
        Account account = stored("jane@example.com", "Jane Doe");
        Author author = byline("jane-doe", "Jane Doe");
        when(accounts.linkAuthorIfUnlinked(account.getId(), author.getId())).thenReturn(1);

        service.ensurePersonalAuthor(account.getId());

        verify(authorService).createPersonal("Jane Doe");
        verify(accounts).linkAuthorIfUnlinked(account.getId(), author.getId());
        verify(authors, never()).deleteById(any());
    }

    @Test
    @DisplayName("AC-7 a request that loses the race deletes its own byline")
    void loserDiscardsItsByline() {
        Account account = stored("jane@example.com", "Jane Doe");
        Author author = byline("jane-doe-2", "Jane Doe");
        when(accounts.linkAuthorIfUnlinked(account.getId(), author.getId())).thenReturn(0);

        service.ensurePersonalAuthor(account.getId());

        verify(authors).deleteById(author.getId());
    }

    @Test
    @DisplayName("an account that already has a byline is returned untouched")
    void alreadyLinked() {
        Account account = stored("jane@example.com", "Jane Doe");
        account.linkAuthor(UUID.randomUUID());

        assertThat(service.ensurePersonalAuthor(account.getId())).isSameAs(account);
        verify(authorService, never()).createPersonal(any());
    }

    @Test
    @DisplayName("name falls back to the email's local part, then to 'Author'")
    void nameFallbacks() {
        Account noName = stored("jane.doe@example.com", null);
        Author author = byline("jane-doe", "jane.doe");
        when(accounts.linkAuthorIfUnlinked(any(), any())).thenReturn(1);
        service.ensurePersonalAuthor(noName.getId());
        verify(authorService).createPersonal("jane.doe");

        Account nothing = stored(null, " ");
        service.ensurePersonalAuthor(nothing.getId());
        verify(authorService).createPersonal("Author");
        assertThat(author).isNotNull();
    }
}

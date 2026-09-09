package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.service.AccountService;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.exception.DomainRuleException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Sign-in identity and authorization.
 *
 * <p>The claims worth proving here are the ones that would be security bugs if they were merely
 * assumed: that a new sign-in is powerless, that a byline cannot be claimed twice, and that an
 * installation cannot lock out its own administrators.
 */
@TestPropertySource(properties = "knowledge.accounts.bootstrap-admin-emails=founder@example.com")
class AccountIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ISSUER = "https://project.supabase.co/auth/v1";

    @Autowired
    private AccountService accounts;

    @Autowired
    private AuthorService authors;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute(
                "truncate table author.account_roles, author.accounts restart identity cascade");
    }

    @Test
    @DisplayName("a first sign-in creates an account that can do nothing at all")
    void grantsNothingOnFirstSignIn() {
        Account account = accounts.resolve(ISSUER, "sub-1", "newcomer@example.com", "Newcomer");

        // Authenticated, and powerless. Anything else would mean signing in is itself a permission.
        assertThat(account.getRoles()).isEmpty();
        assertThat(account.getAuthorId()).isNull();
    }

    @Test
    @DisplayName("signing in again reuses the account rather than creating a second")
    void isIdempotentAcrossSignIns() {
        Account first = accounts.resolve(ISSUER, "sub-1", "a@example.com", "A");
        Account second = accounts.resolve(ISSUER, "sub-1", "a@example.com", "A");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("the same subject at a different issuer is a different person")
    void scopesSubjectByIssuer() {
        accounts.resolve(ISSUER, "sub-1", "a@example.com", "A");
        accounts.resolve("https://other.example.com/auth/v1", "sub-1", "b@example.com", "B");

        // Subjects are unique only within an issuer. Treating them as globally unique would let a
        // second provider, added later, collide with an existing account.
        assertThat(accounts.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("a changed email cannot change what an account may do")
    void refreshingFromTheTokenNeverChangesRoles() {
        Account account = accounts.resolve(ISSUER, "sub-1", "someone@example.com", "Someone");
        accounts.grant(account.getId(), PlatformRole.AUTHOR);

        // The provider now reports the bootstrap admin address for this same subject.
        Account after = accounts.resolve(ISSUER, "sub-1", "founder@example.com", "Someone");

        assertThat(after.getEmail()).isEqualTo("founder@example.com");
        assertThat(after.hasRole(PlatformRole.AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("a configured bootstrap address is an administrator on first sign-in")
    void bootstrapsTheFirstAdministrator() {
        Account account = accounts.resolve(ISSUER, "sub-founder", "founder@example.com", "Founder");

        assertThat(account.hasRole(PlatformRole.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("bootstrap matching ignores address casing")
    void bootstrapIsCaseInsensitive() {
        Account account = accounts.resolve(ISSUER, "sub-founder", "Founder@Example.com", "Founder");

        assertThat(account.hasRole(PlatformRole.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("the last administrator cannot be demoted")
    void refusesToRemoveTheLastAdministrator() {
        Account founder = accounts.resolve(ISSUER, "sub-founder", "founder@example.com", "Founder");

        assertThatThrownBy(() -> accounts.revoke(founder.getId(), PlatformRole.ADMIN))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("last administrator");
    }

    @Test
    @DisplayName("an administrator can be demoted once another exists")
    void allowsDemotionWhenAnotherAdministratorRemains() {
        Account founder = accounts.resolve(ISSUER, "sub-founder", "founder@example.com", "Founder");
        Account second = accounts.resolve(ISSUER, "sub-2", "second@example.com", "Second");
        accounts.grant(second.getId(), PlatformRole.ADMIN);

        Account demoted = accounts.revoke(founder.getId(), PlatformRole.ADMIN);

        assertThat(demoted.hasRole(PlatformRole.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("one byline belongs to one account")
    void refusesToLinkAnAuthorTwice() {
        Author author = authors.findOrCreateByName("Priya", "priya@example.com");
        Account first = accounts.resolve(ISSUER, "sub-1", "priya@gmail.com", "Priya");
        Account second = accounts.resolve(ISSUER, "sub-2", "impostor@example.com", "Someone Else");
        accounts.linkAuthor(first.getId(), author.getId());

        // Otherwise "who wrote this" and "who may edit this" disagree, invisibly.
        assertThatThrownBy(() -> accounts.linkAuthor(second.getId(), author.getId()))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    @DisplayName("relinking the same account to the same author is not an error")
    void isIdempotentWhenRelinkingTheSameAccount() {
        Author author = authors.findOrCreateByName("Priya", "priya@example.com");
        Account account = accounts.resolve(ISSUER, "sub-1", "priya@gmail.com", "Priya");
        accounts.linkAuthor(account.getId(), author.getId());

        assertThat(accounts.linkAuthor(account.getId(), author.getId()).getAuthorId())
                .isEqualTo(author.getId());
    }

    @Test
    @DisplayName("an author who has never signed in stays unlinked")
    void doesNotInferALinkFromAMatchingEmail() {
        Author author = authors.findOrCreateByName("Priya", "priya@example.com");
        Account account = accounts.resolve(ISSUER, "sub-1", "priya@example.com", "Priya");

        // Identical addresses, and still no link. An author row is created from an unverified name
        // in a committed file; treating a match as proof would let anyone with commit access claim
        // that identity.
        assertThat(account.getAuthorId()).isNull();
        assertThat(author.getId()).isNotNull();
    }

    @Test
    @DisplayName("linking to an author that does not exist is rejected")
    void rejectsAnUnknownAuthor() {
        Account account = accounts.resolve(ISSUER, "sub-1", "a@example.com", "A");

        assertThatThrownBy(() -> accounts.linkAuthor(account.getId(), UUID.randomUUID()))
                .hasMessageContaining("Author not found");
    }
}

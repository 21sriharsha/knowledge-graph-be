package com.knowledge.platform.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.service.AccountService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Effective roles and the personal-byline trigger (self-serve authoring, AC-1, AC-5, AC-6). */
class AccountJwtAuthenticationConverterTest {

    private final AccountService accounts = mock(AccountService.class);
    private final AccountJwtAuthenticationConverter converter = new AccountJwtAuthenticationConverter(
            accounts, new TokenRoleExtractor(new OidcConfig.OidcProperties(null, null, null, null, null)));

    private Account account() {
        Account account = Account.of("https://issuer.example/auth/v1", "user-1", "jane@example.com", "Jane");
        when(accounts.resolve(any(), any(), any(), any())).thenReturn(account);
        return account;
    }

    private static List<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("AC-1/AC-6 token AUTHOR is effective and gives an unlinked account a personal byline")
    void tokenAuthorCreatesByline() {
        Account account = account();
        Account linked = Account.of(account.getIssuer(), account.getSubject(), account.getEmail(), "Jane");
        linked.linkAuthor(UUID.randomUUID());
        when(accounts.ensurePersonalAuthor(account.getId())).thenReturn(linked);

        AbstractAuthenticationToken token = converter.convert(
                TokenRoleExtractorTest.token(Map.of("app_roles", List.of("AUTHOR"))));

        assertThat(authorities(token)).containsExactly("ROLE_AUTHOR");
        verify(accounts).ensurePersonalAuthor(account.getId());
    }

    @Test
    @DisplayName("stored ADMIN and token AUTHOR combine; an already linked account gets no new byline")
    void unionWithStoredRoles() {
        Account account = account();
        account.grant(PlatformRole.ADMIN);
        account.linkAuthor(UUID.randomUUID());

        AbstractAuthenticationToken token = converter.convert(
                TokenRoleExtractorTest.token(Map.of("app_roles", List.of("AUTHOR"))));

        assertThat(authorities(token)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_AUTHOR");
        verify(accounts, never()).ensurePersonalAuthor(any());
    }

    @Test
    @DisplayName("AC-5 without the claim only stored roles apply, and no byline is created")
    void noClaimNoRoles() {
        account();

        AbstractAuthenticationToken token = converter.convert(TokenRoleExtractorTest.token(Map.of("role", "authenticated")));

        assertThat(authorities(token)).isEmpty();
        verify(accounts, never()).ensurePersonalAuthor(any());
    }
}

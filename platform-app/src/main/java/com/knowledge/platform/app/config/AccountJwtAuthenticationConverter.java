package com.knowledge.platform.app.config;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.service.AccountService;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a validated access token into this platform's authorities.
 *
 * <p>Much of the point of this class is what it <em>does not</em> read. Supabase issues a
 * {@code role} claim, and it is {@code "authenticated"} for every signed-in user -- it names a
 * Postgres role in the provider's own database, not a permission here. The token also carries user
 * metadata that the user it describes can edit. Neither ever grants anything.
 *
 * <p>Authorities are the union of two sources:
 * <ul>
 *   <li>this platform's records -- the only source of ADMIN (bootstrap address, studio grants);
 *   <li>the provider-written roles claim, limited to token-grantable roles (AUTHOR by default). Every
 *       signed-in user gets AUTHOR this way when the provider's access-token hook is enabled, so the
 *       default is controlled at the provider and existing accounts pick it up on their next token.
 * </ul>
 * See {@link TokenRoleExtractor} and {@code .claude/specs/self-serve-authoring.md}. Token roles are
 * not stored: they are re-read on every request, so withdrawing them at the provider takes effect as
 * the current token expires.
 *
 * <p>An author needs a byline to own anything, so the first request that carries AUTHOR for an
 * account without one gets a personal byline created and linked.
 */
@Slf4j
@Component
public class AccountJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Spring Security's `hasRole` prepends this; authorities must carry it to match. */
    private static final String ROLE_PREFIX = "ROLE_";

    private final AccountService accountService;
    private final TokenRoleExtractor tokenRoles;

    public AccountJwtAuthenticationConverter(AccountService accountService, TokenRoleExtractor tokenRoles) {
        this.accountService = accountService;
        this.tokenRoles = tokenRoles;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Account account = accountService.resolve(
                jwt.getIssuer().toString(),
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                displayNameOf(jwt));

        Set<PlatformRole> roles = EnumSet.noneOf(PlatformRole.class);
        roles.addAll(account.getRoles());
        roles.addAll(tokenRoles.rolesFrom(jwt));

        if (roles.contains(PlatformRole.AUTHOR) && account.getAuthorId() == null) {
            account = accountService.ensurePersonalAuthor(account.getId());
        }

        Collection<GrantedAuthority> authorities = roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role.name()))
                .toList();

        if (authorities.isEmpty()) {
            log.debug("Account {} signed in with no roles", account.getId());
        }
        return new JwtAuthenticationToken(jwt, authorities, account.getId().toString());
    }

    /**
     * A human-readable name, if the provider supplied one.
     *
     * <p>Display only -- it can never affect a permission, so an unverified value is harmless here.
     * Providers disagree about where they put it, hence the fallbacks.
     */
    private String displayNameOf(Jwt jwt) {
        for (String claim : List.of("name", "full_name", "preferred_username")) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        Object metadata = jwt.getClaim("user_metadata");
        if (metadata instanceof java.util.Map<?, ?> map) {
            Object name = map.get("full_name") != null ? map.get("full_name") : map.get("name");
            if (name instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

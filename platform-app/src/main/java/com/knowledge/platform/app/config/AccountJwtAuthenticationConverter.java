package com.knowledge.platform.app.config;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.service.AccountService;
import java.util.Collection;
import java.util.List;
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
 * <p>The whole point of this class is what it <em>does not</em> read. Supabase issues a {@code role}
 * claim, and it is {@code "authenticated"} for every signed-in user -- it names a Postgres role in
 * the provider's own database, not a permission here. The token also carries user metadata that the
 * user it describes can edit. Deriving authorities from either would mean anyone could grant
 * themselves anything.
 *
 * <p>So the token is used for exactly one thing: establishing that the caller controls a subject at
 * a trusted issuer. What that subject may do is looked up here, from this platform's records.
 *
 * <p>A first sign-in creates an account with no roles and is authenticated with no authorities --
 * able to prove who it is and to do nothing at all, until an administrator decides otherwise.
 */
@Slf4j
@Component
public class AccountJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Spring Security's `hasRole` prepends this; authorities must carry it to match. */
    private static final String ROLE_PREFIX = "ROLE_";

    private final AccountService accountService;

    public AccountJwtAuthenticationConverter(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Account account = accountService.resolve(
                jwt.getIssuer().toString(),
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                displayNameOf(jwt));

        Collection<GrantedAuthority> authorities = account.getRoles().stream()
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

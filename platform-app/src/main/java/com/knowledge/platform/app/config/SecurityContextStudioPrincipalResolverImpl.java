package com.knowledge.platform.app.config;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.repository.AccountRepository;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the current caller from the security context.
 *
 * <p>The one place Spring Security meets the domain. Everything downstream receives a
 * {@link StudioPrincipal} value and has no idea a servlet was involved.
 *
 * <p>Two kinds of caller arrive here. A <b>person</b> authenticated with a bearer token, whose
 * account id is the authentication name and whose byline is looked up. A <b>service account</b>
 * authenticated with configured credentials, which has no account row and therefore no byline -- so
 * it owns nothing, and one that genuinely needs full access is configured as an administrator
 * rather than exempted from ownership.
 */
@Component
public class SecurityContextStudioPrincipalResolverImpl implements StudioPrincipalResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AccountRepository accounts;

    public SecurityContextStudioPrincipalResolverImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public StudioPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // Only reachable if a studio path were left unauthenticated by configuration, which is
            // exactly the mistake worth failing loudly on rather than defaulting around.
            throw new IllegalStateException("No authenticated caller on a studio request");
        }

        Set<PlatformRole> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .flatMap(name -> {
                    try {
                        return java.util.stream.Stream.of(PlatformRole.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        // An authority that is not one of ours grants nothing.
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toUnmodifiableSet());

        return accountIdOf(authentication)
                .flatMap(accounts::findById)
                .map(account -> principalFor(account, roles))
                .orElseGet(() -> StudioPrincipal.serviceAccount(roles));
    }

    private StudioPrincipal principalFor(Account account, Set<PlatformRole> roles) {
        return new StudioPrincipal(account.getId(), account.getAuthorId(), roles);
    }

    /**
     * The account id, which the JWT converter puts in the authentication name.
     *
     * <p>A configured service account's name is a username rather than a UUID, so a parse failure
     * here is the normal way of discovering which kind of caller this is.
     */
    private java.util.Optional<UUID> accountIdOf(Authentication authentication) {
        try {
            return java.util.Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}

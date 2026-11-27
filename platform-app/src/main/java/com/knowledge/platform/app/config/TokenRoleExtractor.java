package com.knowledge.platform.app.config;

import com.knowledge.platform.author.model.entity.PlatformRole;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the roles an access token may grant.
 *
 * <p>Exactly one claim is consulted -- {@code knowledge.security.oidc.roles-claim}, written by the
 * identity provider's access-token hook -- and only roles on the configured allow-list survive.
 * Everything else in the token stays ignored for authorization: Supabase's {@code role} claim names
 * a Postgres role in the provider's own database, and {@code user_metadata} is editable by the user
 * it describes, so reading either would let a person grant themselves anything.
 *
 * <p>The claim may be a JSON array or a single string; names match {@link PlatformRole}
 * case-insensitively. A value that is unknown or not grantable grants nothing. It is logged at DEBUG,
 * not WARN, because it arrives on every request a misconfigured hook touches.
 *
 * @see OidcConfig.OidcProperties
 */
@Slf4j
@Component
public class TokenRoleExtractor {

    private final String rolesClaim;
    private final Set<PlatformRole> grantable;

    public TokenRoleExtractor(OidcConfig.OidcProperties properties) {
        this.rolesClaim = properties.rolesClaim();
        this.grantable = properties.tokenGrantableRoles();
    }

    /** The grantable roles this token carries; empty when the claim is absent or grants nothing. */
    public Set<PlatformRole> rolesFrom(Jwt jwt) {
        Collection<?> names = namesIn(jwt.getClaims().get(rolesClaim));
        if (names.isEmpty()) {
            return Set.of();
        }
        Set<PlatformRole> roles = EnumSet.noneOf(PlatformRole.class);
        for (Object name : names) {
            PlatformRole role = roleNamed(name);
            if (role != null && grantable.contains(role)) {
                roles.add(role);
            } else {
                log.debug("Ignoring '{}' in token claim '{}': not a token-grantable role", name, rolesClaim);
            }
        }
        return Set.copyOf(roles);
    }

    private static Collection<?> namesIn(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values;
        }
        if (claim instanceof String value && !value.isBlank()) {
            return List.of(value);
        }
        return List.of();
    }

    private static PlatformRole roleNamed(Object name) {
        if (!(name instanceof String value)) {
            return null;
        }
        try {
            return PlatformRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}

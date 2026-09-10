package com.knowledge.platform.author.model.dto;

import com.knowledge.platform.author.model.entity.PlatformRole;
import java.util.Set;
import java.util.UUID;

/**
 * Who is making a studio request, and what they are entitled to reach.
 *
 * <p>Resolved once at the web boundary and passed down explicitly, rather than read from a thread
 * local inside each service. Passing it makes ownership a visible parameter of every operation that
 * has one -- a service that can be called without a principal is a service whose authorization can
 * be forgotten -- and it means ownership can be tested without a servlet.
 *
 * @param accountId null for a non-human caller authenticating with configured credentials
 * @param authorId the byline this account writes under; null until an administrator links one, and
 *     null for every non-human caller
 */
public record StudioPrincipal(UUID accountId, UUID authorId, Set<PlatformRole> roles) {

    public StudioPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** A caller authenticating with configured credentials rather than as a person. */
    public static StudioPrincipal serviceAccount(Set<PlatformRole> roles) {
        return new StudioPrincipal(null, null, roles);
    }

    public boolean isAdmin() {
        return roles.contains(PlatformRole.ADMIN);
    }

    /**
     * Whether this caller may act on something belonging to {@code ownerAuthorId}.
     *
     * <p>Administrators may act on anything: operating the platform means repairing other people's
     * mistakes, and an administrator who cannot reach a broken repository cannot fix it.
     *
     * <p>Everyone else may act only on their own byline. An account with no linked author therefore
     * owns nothing at all, which is the correct and safe state for someone who has signed in but has
     * not been given a byline -- and for every service account, which is why a service account that
     * genuinely needs full access is configured as an administrator rather than special-cased here.
     *
     * <p>An unowned resource is nobody's, not everybody's. It is reachable only by an administrator.
     */
    public boolean canActOnBehalfOf(UUID ownerAuthorId) {
        if (isAdmin()) {
            return true;
        }
        return authorId != null && authorId.equals(ownerAuthorId);
    }
}

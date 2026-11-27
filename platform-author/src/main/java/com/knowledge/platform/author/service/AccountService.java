package com.knowledge.platform.author.service;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.PlatformRole;
import java.util.List;
import java.util.UUID;

/**
 * Sign-in identities and what they are allowed to do.
 *
 * <p>The platform's authorization boundary. An access token establishes <em>who</em> is calling;
 * everything about <em>what they may do</em> is decided here, from this platform's own records.
 *
 * <p>That split is not incidental. The identity provider's role claim describes its own database
 * rather than this one, and the user metadata it carries is editable by the user it describes. A
 * permission model reading either would let a person grant themselves whatever they liked.
 *
 * <p>The roles stored here are the platform's grants. A request's effective roles may also include
 * AUTHOR from a provider-written token claim (see the app module's {@code TokenRoleExtractor} and
 * {@code .claude/specs/self-serve-authoring.md}); ADMIN only ever comes from here.
 */
public interface AccountService {

    /**
     * Finds or creates the account for a validated token, refreshing its display details.
     *
     * <p>A first sign-in creates an account with no <em>stored</em> roles. What it may do on a
     * given request is these grants plus any token-granted roles.
     */
    Account resolve(String issuer, String subject, String email, String displayName);

    /**
     * Gives an author their own byline if they have none, and returns the account as stored.
     *
     * <p>An author needs a byline to own repositories and articles. The byline is always newly
     * created (see {@link AuthorService#createPersonal}), named after the account's display name,
     * else its email's local part. Safe under concurrent first requests: exactly one byline ends up
     * linked, and a request that loses the race removes the one it created.
     */
    Account ensurePersonalAuthor(UUID accountId);

    List<Account> findAll();

    Account requireById(UUID accountId);

    Account grant(UUID accountId, PlatformRole role);

    /**
     * Removes a role.
     *
     * <p>Refuses to remove the last administrator: an installation with nobody able to grant roles
     * cannot be repaired through the API, only through the database.
     */
    Account revoke(UUID accountId, PlatformRole role);

    /**
     * Links an account to an existing byline, replacing its personal one if it had one.
     *
     * <p>Deliberately administrative, and deliberately not inferred from a matching email address:
     * an author row is created from an unverified name in a committed Markdown file, so treating a
     * match as proof would let anyone with commit access claim an identity.
     */
    Account linkAuthor(UUID accountId, UUID authorId);

    Account unlinkAuthor(UUID accountId);
}

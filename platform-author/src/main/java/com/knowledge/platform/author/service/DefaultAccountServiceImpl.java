package com.knowledge.platform.author.service;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.repository.AccountRepository;
import com.knowledge.platform.author.repository.AuthorRepository;
import com.knowledge.platform.common.exception.DomainRuleException;
import com.knowledge.platform.common.exception.NotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default AccountService.
 *
 * <p>See {@link AccountService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultAccountServiceImpl implements AccountService {

    private final AccountRepository accounts;
    private final AuthorRepository authors;
    private final Set<String> bootstrapAdminEmails;

    public DefaultAccountServiceImpl(
            AccountRepository accounts,
            AuthorRepository authors,
            AccountSecurityProperties properties) {
        this.accounts = accounts;
        this.authors = authors;
        this.bootstrapAdminEmails = properties.normalisedBootstrapAdminEmails();
    }

    @Override
    @Transactional
    public Account resolve(String issuer, String subject, String email, String displayName) {
        Account account = accounts.findByIssuerAndSubject(issuer, subject)
                .orElseGet(() -> {
                    log.info("First sign-in for subject {} at {}; creating an account with no roles",
                            subject, issuer);
                    return accounts.save(Account.of(issuer, subject, email, displayName));
                });

        account.refreshFromToken(email, displayName);
        applyBootstrapAdmin(account);
        return accounts.save(account);
    }

    /**
     * Grants ADMIN to a configured address, so a fresh installation has someone who can grant roles.
     *
     * <p>The chicken-and-egg problem: roles are granted by administrators and a new installation has
     * none. Naming the first one in configuration keeps the decision with whoever controls the
     * deployment rather than with whoever signs in first.
     *
     * <p>This trusts the identity provider to have verified the address. That is a real assumption
     * and the reason the property should be emptied once the first administrator exists -- leaving
     * it set means anyone who can obtain a token for that address becomes an administrator.
     */
    private void applyBootstrapAdmin(Account account) {
        if (account.hasRole(PlatformRole.ADMIN) || account.getEmail() == null) {
            return;
        }
        if (bootstrapAdminEmails.contains(account.getEmail().trim().toLowerCase())) {
            log.warn("Granting ADMIN to {} from the bootstrap-admin-emails configuration. "
                    + "Clear that property once an administrator exists.", account.getEmail());
            account.grant(PlatformRole.ADMIN);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accounts.findAllByOrderByCreatedAtAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Account requireById(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> NotFoundException.of("Account", accountId));
    }

    @Override
    @Transactional
    public Account grant(UUID accountId, PlatformRole role) {
        Account account = requireById(accountId);
        account.grant(role);
        return accounts.save(account);
    }

    @Override
    @Transactional
    public Account revoke(UUID accountId, PlatformRole role) {
        Account account = requireById(accountId);
        if (role == PlatformRole.ADMIN && isLastAdministrator(account)) {
            // Recoverable only by editing the database directly, so it is refused rather than
            // allowed and regretted.
            throw new DomainRuleException(
                    "Refusing to remove the last administrator: no one would be able to grant roles.");
        }
        account.revoke(role);
        return accounts.save(account);
    }

    private boolean isLastAdministrator(Account account) {
        if (!account.hasRole(PlatformRole.ADMIN)) {
            return false;
        }
        return accounts.findAll().stream()
                .filter(other -> other.hasRole(PlatformRole.ADMIN))
                .noneMatch(other -> !other.getId().equals(account.getId()));
    }

    @Override
    @Transactional
    public Account linkAuthor(UUID accountId, UUID authorId) {
        Account account = requireById(accountId);
        if (!authors.existsById(authorId)) {
            throw NotFoundException.of("Author", authorId);
        }
        accounts.findByAuthorId(authorId).ifPresent(existing -> {
            if (!existing.getId().equals(accountId)) {
                // One byline, one account: otherwise "who wrote this" and "who may edit this"
                // disagree, and the disagreement is invisible until it matters.
                throw new DomainRuleException(
                        "That author is already linked to another account.");
            }
        });
        account.linkAuthor(authorId);
        return accounts.save(account);
    }

    @Override
    @Transactional
    public Account unlinkAuthor(UUID accountId) {
        Account account = requireById(accountId);
        account.unlinkAuthor();
        return accounts.save(account);
    }
}

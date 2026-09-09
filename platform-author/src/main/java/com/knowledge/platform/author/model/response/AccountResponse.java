package com.knowledge.platform.author.model.response;

import com.knowledge.platform.author.model.entity.Account;
import com.knowledge.platform.author.model.entity.PlatformRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An account, as an administrator sees it.
 *
 * <p>Carries no token, no provider credential and nothing that could be replayed. {@code subject} is
 * included because it is what an administrator matches against the provider's own dashboard when
 * working out which person an account belongs to.
 */
public record AccountResponse(
        UUID id,
        String issuer,
        String subject,
        String email,
        String displayName,
        Set<PlatformRole> roles,
        UUID authorId,
        Instant createdAt,
        Instant lastSeenAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getIssuer(),
                account.getSubject(),
                account.getEmail(),
                account.getDisplayName(),
                account.getRoles(),
                account.getAuthorId(),
                account.getCreatedAt(),
                account.getLastSeenAt());
    }

    public static List<AccountResponse> from(List<Account> accounts) {
        return accounts.stream().map(AccountResponse::from).toList();
    }
}

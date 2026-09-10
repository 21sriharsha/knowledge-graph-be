package com.knowledge.platform.author.delegate;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.model.response.AccountResponse;
import com.knowledge.platform.author.service.AccountService;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates between the account HTTP contract and the author domain. */
@Component
public class AccountDelegate {

    private final AccountService accountService;
    private final StudioPrincipalResolver principals;

    public AccountDelegate(AccountService accountService, StudioPrincipalResolver principals) {
        this.accountService = accountService;
        this.principals = principals;
    }

    /** The caller's own account. */
    public AccountResponse me() {
        StudioPrincipal principal = principals.require();
        if (principal.accountId() == null) {
            // A configured service account has no account row to describe.
            return new AccountResponse(
                    null, null, null, null, null, principal.roles(), null, null, null);
        }
        return AccountResponse.selfFor(accountService.requireById(principal.accountId()));
    }

    public List<AccountResponse> accounts() {
        return AccountResponse.from(accountService.findAll());
    }

    public AccountResponse grant(UUID accountId, PlatformRole role) {
        return AccountResponse.from(accountService.grant(accountId, role));
    }

    public AccountResponse revoke(UUID accountId, PlatformRole role) {
        return AccountResponse.from(accountService.revoke(accountId, role));
    }

    public AccountResponse linkAuthor(UUID accountId, UUID authorId) {
        return AccountResponse.from(accountService.linkAuthor(accountId, authorId));
    }

    public AccountResponse unlinkAuthor(UUID accountId) {
        return AccountResponse.from(accountService.unlinkAuthor(accountId));
    }
}

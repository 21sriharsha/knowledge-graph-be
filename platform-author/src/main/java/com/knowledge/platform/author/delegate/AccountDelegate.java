package com.knowledge.platform.author.delegate;

import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.model.response.AccountResponse;
import com.knowledge.platform.author.service.AccountService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates between the account HTTP contract and the author domain. */
@Component
public class AccountDelegate {

    private final AccountService accountService;

    public AccountDelegate(AccountService accountService) {
        this.accountService = accountService;
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

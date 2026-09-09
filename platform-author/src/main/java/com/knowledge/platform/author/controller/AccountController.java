package com.knowledge.platform.author.controller;

import com.knowledge.platform.author.delegate.AccountDelegate;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.model.request.LinkAuthorRequest;
import com.knowledge.platform.author.model.response.AccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account administration.
 *
 * <p>Requires ADMIN, enforced by path prefix in the security configuration rather than by an
 * annotation here, so an endpoint added to this controller is protected before it is written.
 *
 * <p>Everything here is a decision a person makes about another person: who may act, and which
 * byline they act as. None of it is inferred from a token, which is the whole reason these endpoints
 * exist rather than the permissions arriving with the sign-in.
 */
@RestController
@RequestMapping("/api/studio/accounts")
@Tag(name = "Accounts", description = "Who may sign in, and what they may do")
public class AccountController {

    private final AccountDelegate accountDelegate;

    public AccountController(AccountDelegate accountDelegate) {
        this.accountDelegate = accountDelegate;
    }

    @GetMapping
    @Operation(summary = "Every account that has signed in",
            description = """
                    Including those with no roles. A new sign-in appears here immediately and can do
                    nothing until granted a role -- this list is where an administrator discovers
                    that someone is waiting.
                    """)
    public List<AccountResponse> accounts() {
        return accountDelegate.accounts();
    }

    @PutMapping("/{accountId}/roles/{role}")
    @Operation(summary = "Grant a role")
    public AccountResponse grant(@PathVariable UUID accountId, @PathVariable PlatformRole role) {
        return accountDelegate.grant(accountId, role);
    }

    @DeleteMapping("/{accountId}/roles/{role}")
    @Operation(summary = "Revoke a role",
            description = """
                    Refuses to remove the last administrator: an installation where nobody can grant
                    roles is repairable only by editing the database.
                    """)
    public AccountResponse revoke(@PathVariable UUID accountId, @PathVariable PlatformRole role) {
        return accountDelegate.revoke(accountId, role);
    }

    @PutMapping("/{accountId}/author")
    @Operation(summary = "Link an account to the byline it writes under",
            description = """
                    Deliberately manual. An author row is created during ingestion from an unverified
                    name in a committed Markdown file, so matching it to a sign-in identity by email
                    would let anyone with commit access claim that identity.

                    One byline, one account: linking an author already claimed elsewhere is refused.
                    """)
    public AccountResponse linkAuthor(
            @PathVariable UUID accountId, @Valid @RequestBody LinkAuthorRequest request) {
        return accountDelegate.linkAuthor(accountId, request.authorId());
    }

    @DeleteMapping("/{accountId}/author")
    @Operation(summary = "Unlink an account from its byline")
    public AccountResponse unlinkAuthor(@PathVariable UUID accountId) {
        return accountDelegate.unlinkAuthor(accountId);
    }
}

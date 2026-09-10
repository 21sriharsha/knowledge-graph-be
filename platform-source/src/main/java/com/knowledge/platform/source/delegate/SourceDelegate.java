package com.knowledge.platform.source.delegate;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.service.StudioPrincipalResolver;
import com.knowledge.platform.common.exception.DomainRuleException;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.request.CreateSourceRequest;
import com.knowledge.platform.source.model.request.UpdateSourceRequest;
import com.knowledge.platform.source.model.response.SourceResponse;
import com.knowledge.platform.source.service.SourceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Translates between the studio HTTP contract and the source domain, and is where a repository
 * stops being anybody's and starts being someone's.
 *
 * <p>This class is the whole studio surface for sources: the controller holds nothing but this, so
 * every by-id operation below passing through {@link SourceService#requireOwned} is what makes
 * ownership hold. The unrestricted {@code requireById} exists for platform-level callers -- webhook
 * dispatch, the reconciliation scheduler, ingestion -- and must not be reached from here.
 *
 * <p>Ownership is checked by fetching, never by asserting. An endpoint that forgets the check has no
 * repository to act on, rather than a repository it should not have.
 */
@Component
public class SourceDelegate {

    private final SourceService sourceService;
    private final StudioPrincipalResolver principals;

    public SourceDelegate(SourceService sourceService, StudioPrincipalResolver principals) {
        this.sourceService = sourceService;
        this.principals = principals;
    }

    public List<SourceResponse> list() {
        return sourceService.findVisible(principals.require()).stream()
                .map(SourceResponse::from)
                .toList();
    }

    public SourceResponse get(UUID id) {
        return SourceResponse.from(sourceService.requireOwned(id, principals.require()));
    }

    /**
     * Connects a repository, owned by the caller.
     *
     * <p>An author does not get to say whose repository this is: the owner is their own byline,
     * whatever the request body claimed. Honouring a submitted owner would let anyone with a studio
     * login create repositories in someone else's name -- and, since articles inherit the
     * repository's owner, publish under their byline.
     *
     * <p>Administrators are the exception, because connecting a repository on behalf of an author
     * being onboarded is exactly what an administrator is for.
     */
    public SourceResponse connect(CreateSourceRequest request) {
        StudioPrincipal principal = principals.require();

        String ownerAuthorName = request.ownerAuthorName();
        String ownerAuthorEmail = request.ownerAuthorEmail();
        UUID ownerAuthorId = null;

        if (!principal.isAdmin()) {
            if (principal.authorId() == null) {
                throw new DomainRuleException(
                        "This account is not linked to an author yet, so it cannot own a "
                                + "repository. An administrator has to link it first.");
            }
            ownerAuthorId = principal.authorId();
            ownerAuthorName = null;
            ownerAuthorEmail = null;
        }

        return SourceResponse.from(sourceService.connect(
                request.sourceType(),
                request.displayName(),
                request.owner(),
                request.repository(),
                request.project(),
                request.defaultBranch(),
                request.contentPath(),
                request.apiBaseUrl(),
                request.accessToken(),
                request.webhookSecret(),
                ownerAuthorName,
                ownerAuthorEmail,
                ownerAuthorId));
    }

    /**
     * Updates a repository the caller owns.
     *
     * <p>Reassigning ownership is an administrator's act. Without that restriction an author could
     * hand a repository to someone else, or take one, by editing a field.
     */
    public SourceResponse update(UUID id, UpdateSourceRequest request) {
        StudioPrincipal principal = principals.require();
        sourceService.requireOwned(id, principal);

        UUID ownerAuthorId = request.ownerAuthorId();
        if (ownerAuthorId != null && !principal.isAdmin()) {
            throw new DomainRuleException(
                    "Only an administrator can change which author owns a repository.");
        }

        return SourceResponse.from(sourceService.update(
                id,
                request.displayName(),
                request.defaultBranch(),
                request.contentPath(),
                request.apiBaseUrl(),
                ownerAuthorId,
                request.accessToken(),
                request.webhookSecret()));
    }

    public SourceResponse setActive(UUID id, boolean active) {
        sourceService.requireOwned(id, principals.require());
        return SourceResponse.from(sourceService.setActive(id, active));
    }

    public void requestSync(UUID id) {
        sourceService.requireOwned(id, principals.require());
        sourceService.requestSync(id, SourceSyncRequestedEvent.Trigger.MANUAL);
    }
}

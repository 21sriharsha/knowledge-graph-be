package com.knowledge.platform.source.integration.strategy;

import com.knowledge.platform.source.integration.adapter.gitlab.GitLabSourceAdapterImpl;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceCredentialCipher;
import org.springframework.stereotype.Component;

/** GitLab application behaviour, delegating every API concern to the GitLab adapter. */
@Component
public class GitLabSourceIntegrationStrategyImpl extends AbstractSourceIntegrationStrategy {

    public GitLabSourceIntegrationStrategyImpl(GitLabSourceAdapterImpl adapter, SourceCredentialCipher credentialCipher) {
        super(adapter, credentialCipher);
    }

    @Override
    public SourceType supportedType() {
        return SourceType.GITLAB;
    }

    /** GitLab push hooks list added, modified and removed paths per commit. */
    @Override
    public boolean supportsIncrementalSync() {
        return true;
    }

    @Override
    public void validateConfiguration(SourceRepository repository) {
        // A GitLab namespace may be nested ("group/subgroup"), which is legitimate and needs no
        // special handling beyond URL encoding in the adapter. The project dimension is not used.
        if (repository.getProject() != null && !repository.getProject().isBlank()) {
            throw new IllegalArgumentException(
                    "GitLab repositories are addressed as namespace/project and take no separate "
                            + "project field; put a nested group in the owner, as 'group/subgroup'");
        }
    }
}

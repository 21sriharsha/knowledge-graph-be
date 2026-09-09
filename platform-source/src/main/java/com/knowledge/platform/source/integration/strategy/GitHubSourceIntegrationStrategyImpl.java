package com.knowledge.platform.source.integration.strategy;

import com.knowledge.platform.source.integration.adapter.github.GitHubSourceAdapterImpl;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceCredentialCipher;
import org.springframework.stereotype.Component;

/** GitHub application behaviour, delegating every API concern to the GitHub adapter. */
@Component
public class GitHubSourceIntegrationStrategyImpl extends AbstractSourceIntegrationStrategy {

    public GitHubSourceIntegrationStrategyImpl(GitHubSourceAdapterImpl adapter, SourceCredentialCipher credentialCipher) {
        super(adapter, credentialCipher);
    }

    @Override
    public SourceType supportedType() {
        return SourceType.GITHUB;
    }

    /** GitHub push payloads list added, modified and removed paths per commit. */
    @Override
    public boolean supportsIncrementalSync() {
        return true;
    }

    @Override
    public void validateConfiguration(SourceRepository repository) {
        if (repository.getProject() != null && !repository.getProject().isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub repositories are addressed as owner/repository and take no project");
        }
    }
}

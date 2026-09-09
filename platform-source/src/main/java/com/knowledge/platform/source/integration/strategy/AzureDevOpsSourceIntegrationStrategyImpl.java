package com.knowledge.platform.source.integration.strategy;

import com.knowledge.platform.source.integration.adapter.azuredevops.AzureDevOpsSourceAdapterImpl;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceCredentialCipher;
import org.springframework.stereotype.Component;

/** Azure DevOps application behaviour, delegating every API concern to the Azure DevOps adapter. */
@Component
public class AzureDevOpsSourceIntegrationStrategyImpl extends AbstractSourceIntegrationStrategy {

    public AzureDevOpsSourceIntegrationStrategyImpl(
            AzureDevOpsSourceAdapterImpl adapter, SourceCredentialCipher credentialCipher) {
        super(adapter, credentialCipher);
    }

    @Override
    public SourceType supportedType() {
        return SourceType.AZURE_DEVOPS;
    }

    /**
     * Azure DevOps service hooks report the commits in a push but not the paths they touched, so
     * there is no incremental path to take: a push here always means a full content walk.
     *
     * <p>This is the one genuinely behavioural difference between the three providers, and it is
     * expressed here rather than discovered by ingestion finding an empty path list.
     */
    @Override
    public boolean supportsIncrementalSync() {
        return false;
    }

    @Override
    public void validateConfiguration(SourceRepository repository) {
        if (repository.getProject() == null || repository.getProject().isBlank()) {
            throw new IllegalArgumentException(
                    "Azure DevOps repositories are addressed as organisation/project/repository and "
                            + "require a project");
        }
    }
}

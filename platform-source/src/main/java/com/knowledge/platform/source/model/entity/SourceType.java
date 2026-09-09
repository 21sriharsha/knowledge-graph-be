package com.knowledge.platform.source.model.entity;

/**
 * A supported external content provider.
 *
 * <p>This enum is the only place the application names a provider. Everything downstream --
 * ingestion, content, the graph -- deals in normalized types, so a GitHub-specific concern can never
 * reach them without first passing through a strategy and an adapter.
 */
public enum SourceType {
    GITHUB,
    GITLAB,
    AZURE_DEVOPS;

    /**
     * Whether the provider addresses repositories through a project namespace.
     *
     * <p>Azure DevOps repositories live at organisation/project/repository; GitHub and GitLab have no
     * project level. Modelling that as a question about the type, rather than as an {@code if} inside
     * adapter code, keeps the difference visible at the boundary where it matters.
     */
    public boolean requiresProject() {
        return this == AZURE_DEVOPS;
    }
}

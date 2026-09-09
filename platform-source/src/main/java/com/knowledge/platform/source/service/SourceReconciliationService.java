package com.knowledge.platform.source.service;

/**
 * Periodic reconciliation between connected repositories and what the platform has materialized.
 *
 * <p>Webhooks are the primary trigger. This is the fallback, and it exists because webhooks are
 * genuinely lossy in ways nothing in this application controls: a provider outage during a push, a
 * delivery rejected while the platform was restarting, a hook deleted or never registered, or a
 * repository connected to an existing repository that has never been pushed to since.
 *
 * <p>Without reconciliation each of those is silent and permanent -- the content simply never
 * appears, and nobody finds out until a reader looks for an article that should exist.
 */
public interface SourceReconciliationService {

    /**
     * Compares each active repository's head revision against the last one materialized, and requests
     * a sync where they differ.
     *
     * @return how many repositories were found to be behind
     */
    int reconcile();
}

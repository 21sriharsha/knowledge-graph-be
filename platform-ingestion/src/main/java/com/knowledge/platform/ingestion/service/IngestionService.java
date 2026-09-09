package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;

/**
 * Drives materialization for a repository.
 *
 * <p>Two paths, chosen by what the provider actually told us:
 *
 * <ul>
 *   <li><b>Incremental</b> -- the push named the files it touched, so only those are fetched. A
 *       one-line edit costs one file read rather than a repository walk.
 *   <li><b>Full walk</b> -- the provider gave no path detail (Azure DevOps never does), or a full
 *       resync was requested.
 * </ul>
 *
 * <p>Both are idempotent. Every document is compared by content hash before any derived state is
 * rewritten, so a full walk over an unchanged repository is cheap rather than merely safe -- which is
 * what makes the full-walk fallback an acceptable default rather than a cost to be avoided.
 *
 * <p>This class deliberately holds no {@code @Transactional} methods. Ingestion spans provider HTTP
 * calls and must not hold a database transaction across them; the transactional units are the
 * persistence handler, {@link IngestionRunRecorder} and {@link ArticleWithdrawalService}.
 */
public interface IngestionService {

    /**
     * Materializes a repository's content.
     *
     * <p>The run row is committed before any work starts, so a crash mid-run leaves a record showing
     * what was attempted rather than no trace at all.
     */
    IngestionRun ingest(SourceSyncRequestedEvent event);
}

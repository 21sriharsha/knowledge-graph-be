package com.knowledge.platform.source.integration.adapter;

import com.knowledge.platform.source.model.entity.SourceType;

/**
 * A provider interaction failed.
 *
 * <p>Every provider-specific exception -- HTTP client errors, JSON parse failures, auth rejections --
 * is translated into this at the adapter boundary. Ingestion therefore has one failure mode to handle
 * rather than three vendors' worth, and the isolation rule in BACKEND-SPEC section 8 ("provider API
 * failures must remain isolated inside adapters") becomes something the type system helps enforce.
 */
public class SourceAdapterException extends RuntimeException {

    private final SourceType sourceType;
    private final boolean retryable;

    public SourceAdapterException(
            SourceType sourceType, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.sourceType = sourceType;
        this.retryable = retryable;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    /**
     * Whether retrying could succeed.
     *
     * <p>A 5xx, a timeout or a rate limit is worth retrying; a 401 or a 404 is not, and retrying it
     * only burns the job executor and the provider's rate limit.
     */
    public boolean isRetryable() {
        return retryable;
    }
}

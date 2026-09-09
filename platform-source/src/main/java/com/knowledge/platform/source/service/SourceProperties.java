package com.knowledge.platform.source.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for external source integration.
 *
 * @param credentialKey base64-encoded 32-byte AES key for provider credentials. Required; supply it
 *     from the environment.
 * @param requestTimeout per-request timeout for provider API calls. Bounded so a hanging provider
 *     occupies a job thread for a known maximum rather than indefinitely.
 * @param maxFileBytes files larger than this are skipped. An article is prose; a multi-megabyte
 *     "Markdown" file is a generated artifact or a mistake, and parsing it would stall ingestion.
 * @param maxTreeEntries upper bound on a single repository walk, so a misconfigured content path
 *     cannot enumerate a monorepo.
 */
@ConfigurationProperties(prefix = "knowledge.source")
public record SourceProperties(
        String credentialKey,
        Duration requestTimeout,
        long maxFileBytes,
        int maxTreeEntries) {

    public SourceProperties {
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        maxFileBytes = maxFileBytes <= 0 ? 2L * 1024 * 1024 : maxFileBytes;
        maxTreeEntries = maxTreeEntries <= 0 ? 5_000 : maxTreeEntries;
    }
}

package com.knowledge.platform.common.service;



/**
 * Produces the deterministic content hashes that make ingestion idempotent.
 *
 * <p>Ingestion compares the hash of incoming source bytes against the hash stored on the canonical
 * article. Equal hashes mean this revision has already been materialized, so the pipeline stops
 * early instead of rewriting every derived representation. The derived tables carry the same hash,
 * which is what makes a stale embedding or search document detectable.
 */
public interface ContentHasher {

    String hash(String content);
}

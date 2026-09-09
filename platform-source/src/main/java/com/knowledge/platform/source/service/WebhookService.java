package com.knowledge.platform.source.service;

import com.knowledge.platform.source.model.dto.WebhookOutcome;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import java.util.UUID;

/**
 * Accepts provider webhooks.
 *
 * <p>Three guards apply in order, and the order is deliberate:
 *
 * <ol>
 *   <li><b>Verify the signature</b>, before the payload is parsed or anything is written. The
 *       endpoint is unauthenticated at the HTTP layer, so this is the only thing establishing that
 *       the caller is the provider.
 *   <li><b>Check the delivery ledger.</b> Providers retry deliveries they believe failed. Without
 *       this a retry re-runs the entire materialization pipeline.
 *   <li><b>Publish a sync request.</b> Nothing is ingested on the webhook thread: a provider expects
 *       a fast acknowledgement and will retry if it does not get one.
 * </ol>
 */
public interface WebhookService {

    WebhookOutcome accept(UUID repositoryId, WebhookRequest request);
}

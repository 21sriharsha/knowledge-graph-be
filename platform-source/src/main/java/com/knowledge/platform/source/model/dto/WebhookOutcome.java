package com.knowledge.platform.source.model.dto;

import java.util.Optional;

/**
 * What a strategy made of an inbound webhook.
 *
 * <p>Three outcomes are deliberately distinct, because they warrant different HTTP responses and
 * different operator attention:
 *
 * <ul>
 *   <li><b>rejected</b> -- the signature did not verify. Possibly an attack, certainly a
 *       misconfiguration. Never processed.
 *   <li><b>ignored</b> -- verified, but an event this platform does not act on (a ping, a tag push,
 *       a push to a non-content branch). Entirely normal, and must be answered 2xx or the provider
 *       will retry it forever.
 *   <li><b>accepted</b> -- verified and actionable, carrying a normalized push.
 * </ul>
 */
public record WebhookOutcome(Status status, PushNotification push, String reason) {

    public enum Status {
        ACCEPTED,
        IGNORED,
        REJECTED
    }

    public static WebhookOutcome accepted(PushNotification push) {
        return new WebhookOutcome(Status.ACCEPTED, push, null);
    }

    public static WebhookOutcome ignored(String reason) {
        return new WebhookOutcome(Status.IGNORED, null, reason);
    }

    public static WebhookOutcome rejected(String reason) {
        return new WebhookOutcome(Status.REJECTED, null, reason);
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    public Optional<PushNotification> pushNotification() {
        return Optional.ofNullable(push);
    }
}

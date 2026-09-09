package com.knowledge.platform.source.model.dto;

import java.util.Locale;
import java.util.Map;

/**
 * An inbound webhook, before any provider has interpreted it.
 *
 * <p>The raw body is kept as the exact bytes received, as a string. Signature verification is
 * computed over exactly what the provider sent, and re-serializing a parsed JSON tree changes
 * whitespace and key order, which invalidates every signature.
 */
public record WebhookRequest(Map<String, String> headers, String rawBody) {

    public WebhookRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** HTTP header names are case-insensitive, and providers are inconsistent about casing. */
    public String header(String name) {
        String direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(lower))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}

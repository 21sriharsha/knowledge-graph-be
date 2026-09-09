package com.knowledge.platform.delivery.model.dto;

import java.util.UUID;

/**
 * What a public URL path resolves to.
 *
 * <p>The frontend asks "what is at this path" and gets a type and a slug, so it can pick a renderer
 * without knowing the platform's URL conventions. That keeps the two from having to agree on path
 * shapes by hand -- the backend owns route resolution, as the delivery module's remit says.
 */
public record RouteTarget(TargetType targetType, UUID targetId, String targetSlug) {

    public enum TargetType {
        ARTICLE,
        AUTHOR,
        TOPIC,
        TAG
    }
}

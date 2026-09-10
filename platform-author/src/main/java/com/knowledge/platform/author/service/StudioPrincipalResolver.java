package com.knowledge.platform.author.service;

import com.knowledge.platform.author.model.dto.StudioPrincipal;

/**
 * Supplies the principal for the request being handled.
 *
 * <p>An interface here, implemented against Spring Security in the application module, because
 * knowing <em>who</em> is calling is an inherently web-layer concern and the domain should not have
 * to import a servlet to ask. It is the same dependency inversion applied to every other external
 * boundary in this codebase.
 */
public interface StudioPrincipalResolver {

    /**
     * The current caller.
     *
     * <p>Throws rather than returning empty when there is no authenticated caller. Every use is on a
     * path that security configuration has already established requires authentication, so an
     * absent principal is a bug in the wiring rather than a case to handle -- and returning an empty
     * principal would silently authorize the request as "owns nothing" instead of failing loudly.
     */
    StudioPrincipal require();
}

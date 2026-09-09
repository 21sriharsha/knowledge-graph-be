package com.knowledge.platform.app.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The backend's security configuration.
 *
 * <p>Studio credentials must come from the environment. There is no default: an application
 * configured without them starts with no studio users, so the studio endpoints reject everything
 * rather than accepting a password an attacker could read in this repository.
 *
 * @param allowedOrigins cross-origin policy for the SSR frontend
 * @param studioUsers author and administrator credentials
 */
@ConfigurationProperties(prefix = "knowledge.security")
public record PlatformSecurityProperties(
        List<String> allowedOrigins, List<StudioUser> studioUsers) {

    public PlatformSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        studioUsers = studioUsers == null ? List.of() : List.copyOf(studioUsers);
    }

    public record StudioUser(String username, String password, List<String> roles) {
        public StudioUser {
            roles = roles == null || roles.isEmpty() ? List.of("AUTHOR") : List.copyOf(roles);
        }
    }
}

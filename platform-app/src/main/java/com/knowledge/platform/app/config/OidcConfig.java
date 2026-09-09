package com.knowledge.platform.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Token validation.
 *
 * <p>Keys are fetched from the provider's JWKS endpoint and cached, never configured here. That
 * matters more than it looks: a shared symmetric secret would have to be distributed to every
 * service that validates a token, and each copy is somewhere it can leak -- and anything holding it
 * can <em>mint</em> tokens, not merely check them. With asymmetric signing this application holds
 * only public keys, so a full compromise of it still cannot forge a login.
 *
 * <p>Key rotation follows from the same choice: the provider rotates, this application refetches,
 * and nothing needs redeploying.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OidcConfig.OidcProperties.class)
public class OidcConfig {

    /**
     * @param issuerUri the provider's issuer, e.g. {@code https://<project>.supabase.co/auth/v1}.
     *     Unset disables token authentication entirely.
     * @param jwkSetUri overrides discovery. Needed for providers whose JWKS is not where the
     *     discovery document says, or when discovery is unreachable at startup.
     * @param audience the expected {@code aud}. Optional, and checked only when set.
     */
    @ConfigurationProperties(prefix = "knowledge.security.oidc")
    public record OidcProperties(String issuerUri, String jwkSetUri, String audience) {
    }

    @Bean
    JwtDecoder jwtDecoder(OidcProperties properties) {
        if (!StringUtils.hasText(properties.issuerUri())) {
            // Returning null leaves the resource server uninstalled; SecurityConfig says so out loud.
            return null;
        }

        NimbusJwtDecoder decoder = StringUtils.hasText(properties.jwkSetUri())
                ? NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build()
                : (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuerUri());

        // The issuer check is the one that matters. Without it any token this decoder can verify
        // would be accepted, including one from an unrelated project that happens to share a key
        // source -- so a valid token for someone else's application would authenticate here.
        OAuth2TokenValidator<Jwt> validator = StringUtils.hasText(properties.audience())
                ? JwtValidators.createDefaultWithValidators(
                        new JwtIssuerValidator(properties.issuerUri()),
                        new JwtAudienceValidator(properties.audience()))
                : JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        decoder.setJwtValidator(validator);

        log.info("OIDC token authentication enabled for issuer {}", properties.issuerUri());
        return decoder;
    }
}

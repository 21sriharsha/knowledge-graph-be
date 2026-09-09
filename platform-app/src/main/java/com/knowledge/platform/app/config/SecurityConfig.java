package com.knowledge.platform.app.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The security boundary.
 *
 * <p>The frontend is never a security boundary, so authorization is decided here and only here.
 * Three zones:
 *
 * <ul>
 *   <li><b>Public read</b> -- {@code GET} on the delivery, search and graph APIs. Anonymous. These
 *       serve only published content; the services themselves report an unpublished article as absent
 *       rather than forbidden, so a 404 does not confirm a slug to someone guessing.
 *   <li><b>Studio</b> -- {@code /api/studio/**}. Requires the AUTHOR role. The path prefix <em>is</em>
 *       the boundary, so a newly added studio endpoint is protected by default rather than by
 *       somebody remembering to annotate it.
 *   <li><b>Account administration</b> -- {@code /api/studio/accounts/**}. Requires ADMIN. Granting
 *       roles and linking accounts to authors is a strictly narrower power than operating sources.
 *   <li><b>Webhooks</b> -- {@code /api/webhooks/**}. Anonymous at the HTTP layer, because a provider
 *       cannot present a session, and authenticated by per-repository signature inside the source
 *       module. The signature is the credential.
 * </ul>
 *
 * <h2>Two ways to authenticate, for two kinds of caller</h2>
 *
 * <p><b>Bearer tokens</b> are how people authenticate. An OIDC provider performs the sign-in --
 * social login or email and password, with the password policy enforced there -- and this
 * application validates the resulting token against the provider's JWKS. It is a resource server
 * only: it never runs an authorization code flow, never holds a client secret, and never sees a
 * password. Roles come from this platform's own records rather than from the token; see
 * {@link AccountJwtAuthenticationConverter} for why that distinction is not optional.
 *
 * <p><b>HTTP Basic</b> remains for non-human callers -- a CI job, a migration script, an operator
 * with curl. Those credentials are configured, not registered, and there is no browser flow behind
 * them. Keeping both is also what lets the frontend migrate to tokens without a flag day.
 *
 * <p>Configure {@code knowledge.security.oidc.issuer-uri} to enable token authentication. With it
 * unset the resource server is not installed at all and only Basic works, which is the correct
 * behaviour for a local run with no identity provider.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            PlatformSecurityProperties properties,
            ObjectProvider<JwtDecoder> jwtDecoder,
            AccountJwtAuthenticationConverter accountConverter) throws Exception {
        http
                // The API is stateless and Basic-authenticated: there is no browser session or cookie
                // for a CSRF token to protect, and a provider webhook could not present one anyway.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // Ordered before the broader studio rule: the first match wins, so account
                        // administration must be named first or it would settle for AUTHOR.
                        .requestMatchers("/api/studio/accounts/**").hasRole("ADMIN")
                        .requestMatchers("/api/studio/**").hasRole("AUTHOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/articles/**", "/api/authors/**", "/api/topics/**",
                                "/api/tags/**", "/api/search/**", "/api/graph/**",
                                "/api/routes/**", "/api/navigation/**")
                        .permitAll()
                        // Recording a read is anonymous by necessity: readers have no identity
                        // here, and requiring one to count a page view would mean building an
                        // identity system to populate a landing-page panel. The exposure is
                        // understood and bounded -- the endpoint accepts no body, writes no
                        // reader data, and the worst an abuser achieves is an inaccurate
                        // trending list. See issue #5 for rate limiting.
                        .requestMatchers(HttpMethod.POST, "/api/articles/*/view").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Metrics expose query patterns and traffic volumes; not public.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // "/v3/api-docs/**" covers the JSON document and its group variants, but
                        // the YAML rendering is a sibling path rather than a child, so it needs
                        // naming explicitly or it 401s while the JSON succeeds.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        // Installed only when an issuer is configured. Without one there is nothing to validate
        // against, and starting a resource server that trusts nobody would fail every request in a
        // way that looks like a bug rather than like missing configuration.
        JwtDecoder decoder = jwtDecoder.getIfAvailable();
        if (decoder != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                    .decoder(decoder)
                    .jwtAuthenticationConverter(accountConverter)));
        } else {
            log.warn("No OIDC issuer configured: only HTTP Basic authentication is available.");
        }
        return http.build();
    }

    /**
     * Studio credentials from configuration.
     *
     * <p>This exists so studio endpoints are authenticated from day one rather than left open with a
     * note promising to secure them later. It is not a production identity provider: the intended
     * replacement is an OIDC resource server, and this bean is the only thing that has to change.
     */
    @Bean
    public UserDetailsService studioUserDetailsService(
            PlatformSecurityProperties properties, PasswordEncoder passwordEncoder) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        if (properties.studioUsers().isEmpty()) {
            log.warn("No knowledge.security.studio-users are configured. Studio and administrative "
                    + "endpoints will reject every request. Configure them through the environment.");
        }
        properties.studioUsers().forEach(user -> manager.createUser(User.withUsername(user.username())
                .password(passwordEncoder.encode(user.password()))
                .roles(user.roles().toArray(String[]::new))
                .build()));
        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource(PlatformSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

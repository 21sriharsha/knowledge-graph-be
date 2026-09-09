package com.knowledge.platform.app.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
 *   <li><b>Webhooks</b> -- {@code /api/webhooks/**}. Anonymous at the HTTP layer, because a provider
 *       cannot present a session, and authenticated by per-repository signature inside the source
 *       module. The signature is the credential.
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, PlatformSecurityProperties properties) throws Exception {
        return http
                // The API is stateless and Basic-authenticated: there is no browser session or cookie
                // for a CSRF token to protect, and a provider webhook could not present one anyway.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/api/studio/**").hasRole("AUTHOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/articles/**", "/api/authors/**", "/api/topics/**",
                                "/api/tags/**", "/api/search/**", "/api/graph/**",
                                "/api/routes/**", "/api/navigation/**")
                        .permitAll()
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
                .httpBasic(Customizer.withDefaults())
                .build();
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

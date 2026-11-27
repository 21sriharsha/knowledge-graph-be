package com.knowledge.platform.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledge.platform.author.model.entity.PlatformRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Token-granted roles (self-serve authoring, AC-1..AC-5). */
class TokenRoleExtractorTest {

    private final TokenRoleExtractor extractor =
            new TokenRoleExtractor(new OidcConfig.OidcProperties(null, null, null, null, null));

    static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "ES256")
                .subject("user-1").issuer("https://issuer.example/auth/v1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    @DisplayName("AC-1 app_roles [AUTHOR] grants AUTHOR, case-insensitively, array or string")
    void grantsAuthorFromClaim() {
        assertThat(extractor.rolesFrom(token(Map.of("app_roles", List.of("AUTHOR"))))).containsExactly(PlatformRole.AUTHOR);
        assertThat(extractor.rolesFrom(token(Map.of("app_roles", List.of("author"))))).containsExactly(PlatformRole.AUTHOR);
        assertThat(extractor.rolesFrom(token(Map.of("app_roles", "Author")))).containsExactly(PlatformRole.AUTHOR);
    }

    @Test
    @DisplayName("AC-2 ADMIN and unknown values in the claim grant nothing")
    void adminAndUnknownGrantNothing() {
        assertThat(extractor.rolesFrom(token(Map.of("app_roles", List.of("ADMIN", "SUPERUSER", 42))))).isEmpty();
        assertThat(extractor.rolesFrom(token(Map.of("app_roles", List.of("ADMIN", "AUTHOR"))))).containsExactly(PlatformRole.AUTHOR);
    }

    @Test
    @DisplayName("AC-3 configuring ADMIN as token-grantable fails at startup")
    void adminCannotBeTokenGrantable() {
        assertThatThrownBy(() -> new OidcConfig.OidcProperties(null, null, null, null, Set.of(PlatformRole.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain ADMIN");
    }

    @Test
    @DisplayName("AC-4 the role and user_metadata claims never grant a role")
    void providerRoleAndUserMetadataIgnored() {
        Jwt jwt = token(Map.of(
                "role", "AUTHOR",
                "user_metadata", Map.of("app_roles", List.of("AUTHOR"), "role", "ADMIN")));
        assertThat(extractor.rolesFrom(jwt)).isEmpty();
    }

    @Test
    @DisplayName("AC-5 no claim (hook not enabled) grants nothing")
    void absentClaimGrantsNothing() {
        assertThat(extractor.rolesFrom(token(Map.of("email", "a@example.com")))).isEmpty();
    }

    @Test
    @DisplayName("defaults: claim app_roles, grantable AUTHOR; a custom claim name is honoured")
    void defaultsAndCustomClaim() {
        OidcConfig.OidcProperties defaults = new OidcConfig.OidcProperties(null, null, null, " ", null);
        assertThat(defaults.rolesClaim()).isEqualTo("app_roles");
        assertThat(defaults.tokenGrantableRoles()).containsExactly(PlatformRole.AUTHOR);

        TokenRoleExtractor custom = new TokenRoleExtractor(
                new OidcConfig.OidcProperties(null, null, null, "platform_roles", null));
        assertThat(custom.rolesFrom(token(Map.of("platform_roles", List.of("AUTHOR"))))).containsExactly(PlatformRole.AUTHOR);
        assertThat(custom.rolesFrom(token(Map.of("app_roles", List.of("AUTHOR"))))).isEmpty();
    }

    @Test
    @DisplayName("an empty grantable set means tokens grant nothing")
    void emptyGrantableSet() {
        TokenRoleExtractor none = new TokenRoleExtractor(new OidcConfig.OidcProperties(null, null, null, null, Set.of()));
        assertThat(none.rolesFrom(token(Map.of("app_roles", List.of("AUTHOR"))))).isEmpty();
    }
}

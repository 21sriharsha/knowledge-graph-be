package com.knowledge.platform.source.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Provider tokens are write credentials to somebody's repository; this is how they are protected. */
class SourceCredentialCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final SourceCredentialCipher cipher = new DefaultSourceCredentialCipherImpl(properties(KEY));

    @Test
    void roundTripsASecret() {
        String token = "ghp_averyrealisticlookingtoken";

        assertThat(cipher.decrypt(cipher.encrypt(token))).isEqualTo(token);
    }

    @Test
    @DisplayName("a fresh IV per encryption means the same secret never produces the same ciphertext")
    void producesDistinctCiphertextForTheSamePlaintext() {
        String token = "same-token";

        String first = cipher.encrypt(token);
        String second = cipher.encrypt(token);

        // Reusing an IV under one key is the single fatal mistake with GCM, so this is the property
        // that matters most here -- identical ciphertext would prove the IV was reused.
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second)).isEqualTo(token);
    }

    @Test
    @DisplayName("GCM authenticates, so tampering fails loudly instead of decrypting to garbage")
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("token");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    void treatsBlankInputAsNothingToStore() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("  ")).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("an unconfigured key fails at startup rather than encrypting with a guessable one")
    void refusesToStartWithoutAKey() {
        assertThatThrownBy(() -> new DefaultSourceCredentialCipherImpl(properties(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credential-key is not configured");
    }

    @Test
    void refusesAKeyOfTheWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString("short".getBytes());

        assertThatThrownBy(() -> new DefaultSourceCredentialCipherImpl(properties(tooShort)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void refusesAKeyThatIsNotBase64() {
        assertThatThrownBy(() -> new DefaultSourceCredentialCipherImpl(properties("not base64 !!!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    private SourceProperties properties(String key) {
        return new SourceProperties(key, Duration.ofSeconds(5), 1024, 100);
    }
}

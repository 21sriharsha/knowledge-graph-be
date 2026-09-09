package com.knowledge.platform.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Content hashing is what makes ingestion idempotent, so determinism is the property under test. */
class ContentHasherTest {

    private final ContentHasher hasher = new DefaultContentHasherImpl();

    @Test
    void producesTheSameHashForTheSameContent() {
        assertThat(hasher.hash("# Title\n\nBody")).isEqualTo(hasher.hash("# Title\n\nBody"));
    }

    @Test
    void producesADifferentHashForDifferentContent() {
        assertThat(hasher.hash("# Title\n\nBody")).isNotEqualTo(hasher.hash("# Title\n\nBody."));
    }

    @Test
    @DisplayName("whitespace is content: a reformatted file is a new revision")
    void treatsWhitespaceAsSignificant() {
        assertThat(hasher.hash("a b")).isNotEqualTo(hasher.hash("a  b"));
    }

    @Test
    void isStableAcrossNonAsciiContent() {
        assertThat(hasher.hash("café")).isEqualTo(hasher.hash("café"));
    }

    @Test
    void producesAHexEncodedSha256() {
        assertThat(hasher.hash("anything")).hasSize(64).matches("[0-9a-f]{64}");
    }
}

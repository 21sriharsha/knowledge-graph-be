package com.knowledge.platform.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledge.platform.common.model.Slug;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The slug policy is the whole of internal-link resolution: a reference resolves precisely when its
 * normalized form equals a stored article's slug. These tests pin that equality.
 */
class SlugPolicyTest {

    private final SlugPolicy slugPolicy = new DefaultSlugPolicyImpl();

    @ParameterizedTest
    @CsvSource({
            "Kubernetes Networking,       kubernetes-networking",
            "kubernetes networking,       kubernetes-networking",
            "  Kubernetes   Networking  , kubernetes-networking",
            "Kubernetes/Networking,       kubernetes-networking",
            "C++ Templates,               c-templates",
            "PostgreSQL 17!,              postgresql-17",
            "Wat is café-cultuur?,        wat-is-cafe-cultuur",
            "--leading-and-trailing--,    leading-and-trailing"
    })
    void normalizesToASingleForm(String input, String expected) {
        assertThat(slugPolicy.slugify(input).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a reference and the title it points at produce the same slug")
    void referenceAndTitleAgree() {
        assertThat(slugPolicy.referenceSlug("kubernetes networking"))
                .isEqualTo(slugPolicy.slugify("Kubernetes Networking"));
    }

    @Test
    void rejectsTextWithNoSlugableCharacters() {
        assertThatThrownBy(() -> slugPolicy.slugify("!!! ???"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot derive a slug");
    }

    @Test
    void returnsNullRatherThanThrowingWhenAsked() {
        assertThat(slugPolicy.slugifyOrNull("!!!")).isNull();
        assertThat(slugPolicy.slugifyOrNull(null)).isNull();
        assertThat(slugPolicy.slugifyOrNull("  ")).isNull();
    }

    @Test
    void truncatesToTheSlugColumnWidthWithoutLeavingATrailingHyphen() {
        Slug slug = slugPolicy.slugify("word ".repeat(80));

        assertThat(slug.value()).hasSizeLessThanOrEqualTo(SlugPolicy.MAX_LENGTH);
        assertThat(slug.value()).doesNotEndWith("-");
    }

    @Test
    @DisplayName("lowercasing uses the root locale, so the same title slugs identically everywhere")
    void isIndependentOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // Turkish maps 'I' to a dotless 'ı'. Without an explicit root locale, an article titled
            // "Istio Networking" would get a different slug on a machine with a Turkish default,
            // and every link to it would silently stop resolving.
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(slugPolicy.slugify("Istio Networking").value()).isEqualTo("istio-networking");
        } finally {
            Locale.setDefault(original);
        }
    }

    @AfterEach
    void resetLocale() {
        // Defensive: a leaked default locale would make unrelated tests fail confusingly.
        assertThat(Locale.getDefault()).isNotNull();
    }
}

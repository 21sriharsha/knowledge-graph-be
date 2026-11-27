package com.knowledge.platform.content.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scalar versus list semantics of front-matter values (bug fix D2): commas split list keys only.
 */
class FrontmatterTest {

    @Test
    @DisplayName("AC-4 a scalar value keeps its commas")
    void firstKeepsCommas() {
        Frontmatter frontmatter = new Frontmatter(Map.of(
                "summary", List.of("Combining lexical and semantic retrieval, and why the scores must be normalized.")));

        assertThat(frontmatter.first("summary"))
                .contains("Combining lexical and semantic retrieval, and why the scores must be normalized.");
    }

    @Test
    @DisplayName("a title with a comma is not truncated")
    void titleWithCommaIsIntact() {
        Frontmatter frontmatter = new Frontmatter(Map.of("title", List.of("Raft, explained")));

        assertThat(frontmatter.first("title")).contains("Raft, explained");
    }

    @Test
    @DisplayName("AC-4 the single-line list form still splits on commas")
    void allSplitsCommaSeparatedLists() {
        Frontmatter frontmatter = new Frontmatter(Map.of("tags", List.of("postgres, indexing")));

        assertThat(frontmatter.all("tags")).containsExactly("postgres", "indexing");
    }

    @Test
    @DisplayName("the YAML list form works for both list and scalar reads")
    void yamlListForm() {
        Frontmatter frontmatter = new Frontmatter(Map.of(
                "tags", List.of("postgres", "search"),
                "author", List.of("Anita Rao", "Second Entry")));

        assertThat(frontmatter.all("tags")).containsExactly("postgres", "search");
        assertThat(frontmatter.first("author")).contains("Anita Rao");
    }

    @Test
    @DisplayName("first skips blank values, trims, and is case-insensitive on the key")
    void firstSkipsBlanksAndTrims() {
        Frontmatter frontmatter = new Frontmatter(Map.of("Summary", List.of("  ", "  Trimmed, still whole.  ")));

        assertThat(frontmatter.first("summary")).contains("Trimmed, still whole.");
        assertThat(frontmatter.first("missing")).isEmpty();
    }
}

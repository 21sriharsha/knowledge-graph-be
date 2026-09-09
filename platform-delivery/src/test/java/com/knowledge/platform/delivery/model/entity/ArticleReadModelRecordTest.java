package com.knowledge.platform.delivery.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A stored read model is reusable only when both the article and the payload shape are current.
 *
 * <p>The second half is the interesting one: the read model can change without the article
 * changing. Before schema versioning, a payload written by an older assembler stayed hash-current
 * and was served until the author happened to edit the article -- so a newly added field was simply
 * absent from the page, with nothing to indicate why.
 */
class ArticleReadModelRecordTest {

    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    @Test
    void matchesWhenBothTheRevisionAndTheShapeAreCurrent() {
        ArticleReadModelRecord record = record(HASH, ArticleReadModel.SCHEMA_VERSION);

        assertThat(record.matches(HASH, ArticleReadModel.SCHEMA_VERSION)).isTrue();
    }

    @Test
    void isStaleWhenTheArticleHasBeenRevised() {
        ArticleReadModelRecord record = record(HASH, ArticleReadModel.SCHEMA_VERSION);

        assertThat(record.matches(OTHER_HASH, ArticleReadModel.SCHEMA_VERSION)).isFalse();
    }

    @Test
    void isStaleWhenThePayloadShapeChangedEvenThoughTheArticleDidNot() {
        ArticleReadModelRecord record = record(HASH, ArticleReadModel.SCHEMA_VERSION - 1);

        assertThat(record.matches(HASH, ArticleReadModel.SCHEMA_VERSION)).isFalse();
    }

    @Test
    void replaceAdoptsTheCurrentRevisionAndShape() {
        ArticleReadModelRecord record = record(HASH, ArticleReadModel.SCHEMA_VERSION - 1);

        record.replace("{\"slug\":\"x\"}", OTHER_HASH, ArticleReadModel.SCHEMA_VERSION);

        assertThat(record.matches(OTHER_HASH, ArticleReadModel.SCHEMA_VERSION)).isTrue();
        assertThat(record.getSchemaVersion()).isEqualTo(ArticleReadModel.SCHEMA_VERSION);
    }

    private ArticleReadModelRecord record(String contentHash, int schemaVersion) {
        return new ArticleReadModelRecord(UUID.randomUUID(), "{}", contentHash, schemaVersion);
    }
}

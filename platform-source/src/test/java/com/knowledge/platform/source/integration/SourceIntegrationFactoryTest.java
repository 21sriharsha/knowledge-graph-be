package com.knowledge.platform.source.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledge.platform.source.integration.strategy.SourceIntegrationStrategy;
import com.knowledge.platform.source.model.dto.BinaryAsset;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookOutcome;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The factory is the only place a {@link SourceType} maps to behaviour. Its most valuable property is
 * the one it enforces at construction: adding a provider without its strategy fails at startup rather
 * than on the first webhook that provider ever sends.
 */
class SourceIntegrationFactoryTest {

    @Test
    void selectsTheStrategyForEachProvider() {
        SourceIntegrationFactory factory = new SourceIntegrationFactory(List.of(
                stub(SourceType.GITHUB), stub(SourceType.GITLAB), stub(SourceType.AZURE_DEVOPS)));

        for (SourceType type : SourceType.values()) {
            assertThat(factory.strategyFor(type).supportedType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("a SourceType with no strategy fails the application context, not the first request")
    void refusesToStartWithAnUnservedProvider() {
        assertThatThrownBy(() -> new SourceIntegrationFactory(
                List.of(stub(SourceType.GITHUB), stub(SourceType.GITLAB))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_DEVOPS");
    }

    @Test
    void refusesTwoStrategiesClaimingTheSameProvider() {
        assertThatThrownBy(() -> new SourceIntegrationFactory(List.of(
                stub(SourceType.GITHUB), stub(SourceType.GITHUB),
                stub(SourceType.GITLAB), stub(SourceType.AZURE_DEVOPS))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two strategies claim");
    }

    private SourceIntegrationStrategy stub(SourceType type) {
        return new SourceIntegrationStrategy() {
            @Override
            public SourceType supportedType() {
                return type;
            }

            @Override
            public boolean supportsIncrementalSync() {
                return true;
            }

            @Override
            public void validateConfiguration(SourceRepository repository) {
            }

            @Override
            public Optional<String> resolveCurrentRevision(SourceRepository repository) {
                return Optional.empty();
            }

            @Override
            public Optional<BinaryAsset> readAsset(
                    SourceRepository repository, String path, String revision) {
                return Optional.empty();
            }

            @Override
            public List<SourceFile> fetchContentSnapshot(SourceRepository repository, String revision) {
                return List.of();
            }

            @Override
            public Optional<SourceFile> fetchFile(
                    SourceRepository repository, String path, String revision) {
                return Optional.empty();
            }

            @Override
            public WebhookOutcome interpretWebhook(
                    SourceRepository repository, WebhookRequest request) {
                return WebhookOutcome.ignored("stub");
            }
        };
    }
}

package com.knowledge.platform.ai.adapter.springai;

import com.knowledge.platform.ai.service.AiProperties;
import com.knowledge.platform.ai.service.TextEmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Embedding generation through Spring AI's embedding abstraction.
 *
 * <p>Dimension is checked on every call rather than trusted from configuration. The schema fixes the
 * vector column at a specific width, and a provider returning a different width would otherwise fail
 * deep inside a batch insert with an error that says nothing about the cause. Rejecting it here
 * names the problem.
 */
@Slf4j
@Service
public class SpringAiTextEmbeddingModelImpl implements TextEmbeddingModel {

    /**
     * Embedding models truncate silently past their context window, so the cheapest correct thing is
     * to bound the input ourselves and know that we did. Roughly the first 8k characters of an
     * article carry its subject; the tail rarely changes what it is about.
     */
    private static final int MAX_INPUT_CHARACTERS = 8_000;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AiProperties properties;
    private final MeterRegistry meterRegistry;

    public SpringAiTextEmbeddingModelImpl(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            AiProperties properties,
            MeterRegistry meterRegistry) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (!properties.enabled() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return Optional.empty();
        }

        String bounded = text.length() > MAX_INPUT_CHARACTERS
                ? text.substring(0, MAX_INPUT_CHARACTERS)
                : text;

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            float[] vector = model.embed(bounded);
            if (vector.length != properties.embeddingDimensions()) {
                sample.stop(meterRegistry.timer("knowledge.ai.embedding", "outcome", "dimension_mismatch"));
                log.error("Embedding provider returned {} dimensions but the schema expects {}. "
                                + "Changing embedding model requires a migration, not a config change.",
                        vector.length, properties.embeddingDimensions());
                return Optional.empty();
            }
            sample.stop(meterRegistry.timer("knowledge.ai.embedding", "outcome", "success"));
            return Optional.of(vector);
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("knowledge.ai.embedding", "outcome", "failure"));
            // The embedding job retries, and an article stays publishable without its vector, so this
            // is a degradation rather than a failure.
            log.warn("Embedding generation unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int dimensions() {
        return properties.embeddingDimensions();
    }

    @Override
    public String modelName() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        return model == null ? "unavailable" : model.getClass().getSimpleName();
    }

    @Override
    public boolean isAvailable() {
        return properties.enabled() && embeddingModelProvider.getIfAvailable() != null;
    }
}

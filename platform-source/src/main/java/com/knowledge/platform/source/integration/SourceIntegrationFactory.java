package com.knowledge.platform.source.integration;

import com.knowledge.platform.source.integration.strategy.SourceIntegrationStrategy;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Selects the strategy for a repository's provider.
 *
 * <p>This is the only place in the application that maps a {@link SourceType} to behaviour. Every
 * other module asks the factory and receives a provider-neutral strategy, which is what keeps
 * {@code switch (sourceType)} from spreading through ingestion.
 *
 * <p>Completeness is checked at startup rather than at first use. Adding a {@code SourceType}
 * constant without its strategy then fails the application context with a message naming the missing
 * provider, instead of throwing on the first webhook that provider ever sends -- possibly weeks
 * later, in production, inside a background job.
 */
@Component
public class SourceIntegrationFactory {

    private final Map<SourceType, SourceIntegrationStrategy> strategies;

    public SourceIntegrationFactory(List<SourceIntegrationStrategy> availableStrategies) {
        Map<SourceType, SourceIntegrationStrategy> byType = new EnumMap<>(SourceType.class);
        for (SourceIntegrationStrategy strategy : availableStrategies) {
            SourceIntegrationStrategy previous = byType.put(strategy.supportedType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Two strategies claim " + strategy.supportedType()
                        + ": " + previous.getClass().getName() + " and " + strategy.getClass().getName());
            }
        }

        List<SourceType> missing = java.util.Arrays.stream(SourceType.values())
                .filter(type -> !byType.containsKey(type))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "No SourceIntegrationStrategy is registered for: " + missing
                            + ". Every SourceType must have one, or a repository of that type would "
                            + "fail only when it is first used.");
        }

        this.strategies = Map.copyOf(byType);
    }

    public SourceIntegrationStrategy strategyFor(SourceType sourceType) {
        SourceIntegrationStrategy strategy = strategies.get(sourceType);
        if (strategy == null) {
            throw new IllegalStateException("No strategy registered for " + sourceType);
        }
        return strategy;
    }

    public SourceIntegrationStrategy strategyFor(SourceRepository repository) {
        return strategyFor(repository.getSourceType());
    }
}

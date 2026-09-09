package com.knowledge.platform.app.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds on the in-process job executor.
 *
 * <p>The queue capacity is finite on purpose. An unbounded queue turns a burst of ingestion work into
 * unbounded heap growth and hides the back-pressure an operator needs to see; a rejection is
 * recoverable and visible, an OutOfMemoryError is neither.
 */
@ConfigurationProperties(prefix = "knowledge.jobs")
public record JobExecutionProperties(
        @Min(1) int corePoolSize,
        @Min(1) int maxPoolSize,
        @Min(1) int queueCapacity,
        @Min(1) int schedulerPoolSize,
        @Min(1) int awaitTerminationSeconds) {

    public JobExecutionProperties {
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "knowledge.jobs.max-pool-size must be at least core-pool-size");
        }
    }
}

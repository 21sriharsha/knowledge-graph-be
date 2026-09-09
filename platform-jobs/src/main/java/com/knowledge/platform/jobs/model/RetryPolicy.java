package com.knowledge.platform.jobs.model;

import java.time.Duration;

/**
 * How many times a failed job may be retried, and how long to wait between attempts.
 *
 * <p>Only work that is safe to repeat should ask for retries. The queue makes no attempt to reason
 * about safety on a job's behalf -- it cannot -- so the default is a single attempt and retries are
 * something a job opts into.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double backoffMultiplier) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be non-null and non-negative");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be at least 1.0");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, 1.0);
    }

    public static RetryPolicy exponential(int maxAttempts, Duration initialBackoff) {
        return new RetryPolicy(maxAttempts, initialBackoff, 2.0);
    }

    /** Delay before the given 1-based attempt. Attempt 1 never waits. */
    public Duration backoffBeforeAttempt(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }
        double factor = Math.pow(backoffMultiplier, attempt - 2.0);
        return Duration.ofMillis(Math.round(initialBackoff.toMillis() * factor));
    }
}

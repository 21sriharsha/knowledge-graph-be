package com.knowledge.platform.ai.model.dto;

import java.time.LocalDate;

/**
 * An optional publication-date window ("last year", "since 2024").
 *
 * <p>Both bounds are nullable and inclusive; either side alone is a valid open-ended range.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static DateRange unbounded() {
        return new DateRange(null, null);
    }

    public boolean isUnbounded() {
        return from == null && to == null;
    }

    /** Whether the range is self-contradictory, which model output occasionally is. */
    public boolean isInverted() {
        return from != null && to != null && from.isAfter(to);
    }
}

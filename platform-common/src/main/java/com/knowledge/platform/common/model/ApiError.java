package com.knowledge.platform.common.model;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every endpoint returns.
 *
 * <p>A stable error contract matters as much as the success contract: the SSR frontend has to
 * distinguish "this slug does not exist" (render a 404 page) from "the backend is broken" (render an
 * error boundary) without parsing prose. The {@code code} is the field to branch on; {@code message}
 * is for humans.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> violations) {

    public ApiError {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }

    public static ApiError withViolations(
            int status, String code, String message, String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, code, message, path, violations);
    }

    /** A single failed constraint on a request payload. */
    public record FieldViolation(String field, String message) {
    }
}

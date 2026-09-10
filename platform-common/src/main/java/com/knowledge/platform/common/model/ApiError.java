package com.knowledge.platform.common.model;

import com.knowledge.platform.common.web.RequestCorrelationFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/**
 * The single error shape every endpoint returns.
 *
 * <p>A stable error contract matters as much as the success contract: the SSR frontend has to
 * distinguish "this slug does not exist" (render a 404 page) from "the backend is broken" (render an
 * error boundary) without parsing prose. The {@code code} is the field to branch on; {@code message}
 * is for humans.
 *
 * <p>{@code requestId} is what makes a reported failure findable. Pages render on the server, so a
 * reader who sees an error has nothing to send but a screenshot -- this turns that into one grep.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> violations) {

    public ApiError {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, currentRequestId(), List.of());
    }

    public static ApiError withViolations(
            int status, String code, String message, String path, List<FieldViolation> violations) {
        return new ApiError(
                Instant.now(), status, code, message, path, currentRequestId(), violations);
    }

    /** Read from the logging context, so the id in the response is the id in the log line. */
    private static String currentRequestId() {
        return MDC.get(RequestCorrelationFilter.MDC_KEY);
    }

    /** A single failed constraint on a request payload. */
    public record FieldViolation(String field, String message) {
    }
}

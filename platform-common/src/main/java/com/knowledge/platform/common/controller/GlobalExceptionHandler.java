package com.knowledge.platform.common.controller;

import com.knowledge.platform.common.exception.DomainRuleException;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Translates domain and validation failures into the {@link ApiError} contract. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Caller-supplied text echoed into an error body is bounded, so a huge parameter can't bloat it. */
    private static final int MAX_ECHOED_PROPERTY_LENGTH = 100;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", e.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(DomainRuleException.class)
    public ResponseEntity<ApiError> handleDomainRule(DomainRuleException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "DOMAIN_RULE_VIOLATION", e.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.withViolations(HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
                        "Request validation failed", request.getRequestURI(), violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(
            ConstraintViolationException e, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = e.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.withViolations(HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
                        "Request validation failed", request.getRequestURI(), violations));
    }

    /**
     * An unknown property in a {@code sort} parameter is the caller's mistake, not a server failure.
     *
     * <p>Spring Data raises this while building the query; without a mapping it fell through to the
     * catch-all and answered 500 with an ERROR log. The message names the property but not the entity
     * type Spring Data mentions, which is an internal detail.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownSortProperty(
            PropertyReferenceException e, HttpServletRequest request) {
        String property = abbreviate(e.getPropertyName(), MAX_ECHOED_PROPERTY_LENGTH);
        log.warn("Rejected unknown sort property '{}' on {}", property, request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST",
                        "Unknown sort property '" + property + "'", request.getRequestURI()));
    }

    /**
     * A request for a path no controller maps is a 404, not a server failure.
     *
     * <p>Spring MVC hands unmatched paths to the static-resource handler, which raises this; without a
     * mapping it reached the catch-all and answered 500 with an ERROR log and stack trace. Logged at
     * DEBUG: unknown paths are routine (typos, crawlers, scanners) and must not page anyone.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleUnknownPath(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("No handler for {} {}", e.getHttpMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "No such endpoint",
                        request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST", e.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
        // The response message is deliberately generic. Internal failure detail routinely contains
        // connection strings, provider identifiers and SQL fragments; that belongs in the log, which
        // is access-controlled, not in a body served to an anonymous reader.
        log.error("Unhandled failure serving {}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                        "The request could not be completed", request.getRequestURI()));
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}

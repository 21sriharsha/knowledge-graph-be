package com.knowledge.platform.common.controller;

import com.knowledge.platform.common.exception.DomainRuleException;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates domain and validation failures into the {@link ApiError} contract. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
}

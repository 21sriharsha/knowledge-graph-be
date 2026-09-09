package com.knowledge.platform.common.exception;

/** Raised when a requested canonical or derived resource does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object identifier) {
        return new NotFoundException(resource + " not found: " + identifier);
    }
}

package com.knowledge.platform.common.exception;

/** Raised when a request is well-formed but violates a domain rule. */
public class DomainRuleException extends RuntimeException {

    public DomainRuleException(String message) {
        super(message);
    }
}

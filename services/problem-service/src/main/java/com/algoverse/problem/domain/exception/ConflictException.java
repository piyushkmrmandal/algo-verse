package com.algoverse.problem.domain.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String resourceName, String field, Object value) {
        super(String.format("%s already exists with %s: '%s'", resourceName, field, value));
    }
}

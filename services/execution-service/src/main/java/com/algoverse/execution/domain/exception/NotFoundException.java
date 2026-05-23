package com.algoverse.execution.domain.exception;

/**
 * Thrown when a requested resource (submission, problem, test case) cannot
 * be found. Maps to HTTP 404 Not Found.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

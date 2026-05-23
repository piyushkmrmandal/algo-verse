package com.algoverse.execution.domain.exception;

/**
 * Thrown when a user exceeds their per-hour submission quota.
 * Maps to HTTP 429 Too Many Requests.
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }

    public QuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

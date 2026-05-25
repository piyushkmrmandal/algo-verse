package com.algoverse.submission.domain;

/**
 * All possible lifecycle states of a code submission.
 */
public enum SubmissionStatus {
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR
}

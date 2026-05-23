package com.algoverse.execution.application.dto;

/**
 * Response body for {@code POST /api/v1/submissions/run}.
 *
 * @param stdout    Captured standard output from the user program.
 * @param stderr    Captured standard error (compiler messages, stack traces).
 * @param runtimeMs Wall-clock execution time in milliseconds.
 * @param success   True when the program exited with code 0 and did not time out.
 */
public record RunResultDto(
        String stdout,
        String stderr,
        long runtimeMs,
        boolean success
) {}

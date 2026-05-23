package com.algoverse.execution.infrastructure.sandbox;

/**
 * Immutable value object returned by {@link SandboxRunner} after a single
 * sandbox invocation.
 *
 * @param stdout    Captured standard output from the user program.
 * @param stderr    Captured standard error (compiler messages, stack traces).
 * @param exitCode  OS exit code: 0 = success, 124 = TLE (timeout), other = error.
 * @param runtimeMs Wall-clock time measured by the JVM around the process, in ms.
 * @param timedOut  True when the process exceeded {@code timeoutSeconds} and was
 *                  forcibly destroyed.
 */
public record ExecutionResult(
        String stdout,
        String stderr,
        int exitCode,
        long runtimeMs,
        boolean timedOut
) {

    /** Convenience: true when exit code is 0 and the process was not killed. */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }
}

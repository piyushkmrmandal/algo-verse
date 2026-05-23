package com.algoverse.execution.infrastructure.sandbox;

import com.algoverse.execution.domain.model.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Core sandbox execution engine.
 *
 * <p>Launches an ephemeral Docker container using the configured gVisor runtime
 * ({@code runsc}) with strict resource limits (CPU, memory, tmpfs, ulimits, no
 * network). The sandbox image is responsible for compiling/interpreting the code
 * and enforcing the per-language time limit via an internal watchdog.
 *
 * <p>This service adds an outer JVM-level timeout of
 * {@code config.timeoutSeconds + 2} seconds as a safety net to guarantee the
 * process is reaped even if the inner watchdog fails.
 */
@Slf4j
@Service
public class SandboxRunner {

    /** Maps AlgoVerse language tokens to sandbox image runner identifiers. */
    private static final Map<Language, String> RUNNER_MAP = Map.of(
            Language.PYTHON,     "python3",
            Language.JAVA,       "java",
            Language.CPP,        "cpp",
            Language.JAVASCRIPT, "node",
            Language.GO,         "go",
            Language.RUST,       "rust"
    );

    /** Maximum bytes captured from stdout/stderr to prevent OOM in the service. */
    private static final int MAX_OUTPUT_BYTES = 64 * 1024; // 64 KB

    /**
     * Executes {@code code} in an isolated Docker container and returns the result.
     *
     * @param code     User-submitted source code.
     * @param language Programming language — used to select the runner binary.
     * @param input    Standard input to pipe into the container (test case input).
     * @param config   Sandbox resource limits and image configuration.
     * @return An {@link ExecutionResult} with stdout, stderr, exit code, timing, and
     *         a flag indicating whether the process timed out.
     */
    public ExecutionResult run(String code, Language language, String input, SandboxConfig config) {
        String runner = RUNNER_MAP.getOrDefault(language, "python3");
        List<String> command = buildCommand(config, runner);

        log.debug("Launching sandbox: language={} runner={} memoryLimitMb={} timeoutSeconds={}",
                language, runner, config.getMemoryLimitMb(), config.getTimeoutSeconds());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().clear(); // no env leak into container
        pb.redirectErrorStream(false);

        long startMs = System.currentTimeMillis();
        Process process = null;
        boolean timedOut = false;

        try {
            process = pb.start();

            // Write code via stdin — the sandbox image reads code from stdin,
            // then reads input data after a delimiter. Protocol:
            //   <code>\n---INPUT---\n<input>
            try (OutputStream stdin = process.getOutputStream()) {
                String payload = code + "\n---INPUT---\n" + (input == null ? "" : input);
                stdin.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            // Outer JVM timeout: config.timeoutSeconds + 2 second buffer.
            long outerTimeoutSeconds = config.getTimeoutSeconds() + 2L;
            boolean finished = process.waitFor(outerTimeoutSeconds, TimeUnit.SECONDS);

            long runtimeMs = System.currentTimeMillis() - startMs;

            if (!finished) {
                timedOut = true;
                process.destroyForcibly();
                log.warn("Sandbox process killed after {}s timeout (language={})", outerTimeoutSeconds, language);
                return new ExecutionResult("", "Time limit exceeded", 124, runtimeMs, true);
            }

            int exitCode = process.exitValue();
            String stdout = readCapped(process.getInputStream());
            String stderr  = readCapped(process.getErrorStream());

            // Exit code 124 is the standard shell timeout exit code.
            if (exitCode == 124) {
                timedOut = true;
            }

            log.debug("Sandbox completed: exitCode={} runtimeMs={} timedOut={}", exitCode, runtimeMs, timedOut);
            return new ExecutionResult(stdout, stderr, exitCode, runtimeMs, timedOut);

        } catch (IOException e) {
            long runtimeMs = System.currentTimeMillis() - startMs;
            log.error("Sandbox I/O error for language={}: {}", language, e.getMessage(), e);
            return new ExecutionResult("", "System error: " + e.getMessage(), 1, runtimeMs, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long runtimeMs = System.currentTimeMillis() - startMs;
            log.error("Sandbox interrupted for language={}", language, e);
            if (process != null) {
                process.destroyForcibly();
            }
            return new ExecutionResult("", "Execution interrupted", 1, runtimeMs, false);

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<String> buildCommand(SandboxConfig config, String runner) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");

        // gVisor (or runc in dev) runtime
        cmd.add("--runtime=" + config.getRuntime());

        // Memory cap
        cmd.add("--memory=" + config.getMemoryLimitMb() + "m");
        cmd.add("--memory-swap=" + config.getMemoryLimitMb() + "m"); // disable swap

        // CPU fraction
        cmd.add("--cpus=" + config.getCpuLimit());

        // Network isolation
        if (config.isNetworkDisabled()) {
            cmd.add("--network=none");
        }

        // Read-only root filesystem — code is compiled to /tmp
        cmd.add("--read-only");

        // Writable /tmp with hard size cap
        cmd.add("--tmpfs=/tmp:size=64m,mode=1777");

        // Resource ulimits: file descriptors and processes
        cmd.add("--ulimit");
        cmd.add("nofile=64:64");
        cmd.add("--ulimit");
        cmd.add("nproc=50:50");

        // Disable privilege escalation
        cmd.add("--security-opt=no-new-privileges");

        // Drop all capabilities
        cmd.add("--cap-drop=ALL");

        // Pipe stdin so we can feed code + input
        cmd.add("-i");

        // Image
        cmd.add(config.getDockerImage());

        // Runner binary (handled by the image ENTRYPOINT)
        cmd.add(runner);

        // Pass timeout to the inner watchdog inside the image
        cmd.add(String.valueOf(config.getTimeoutSeconds()));

        return cmd;
    }

    /**
     * Reads an input stream up to {@code MAX_OUTPUT_BYTES}, then discards the
     * rest. Prevents runaway output from crashing the service with OOM.
     */
    private String readCapped(java.io.InputStream is) {
        try {
            byte[] buf = is.readNBytes(MAX_OUTPUT_BYTES);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read sandbox output stream: {}", e.getMessage());
            return "";
        }
    }
}

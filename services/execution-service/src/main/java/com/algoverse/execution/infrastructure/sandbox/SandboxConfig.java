package com.algoverse.execution.infrastructure.sandbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Docker-based gVisor sandbox.
 *
 * <p>Bound from the {@code sandbox.*} namespace in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "sandbox")
@Getter
@Setter
public class SandboxConfig {

    /** Docker image used to execute untrusted code (e.g. {@code algoverse/sandbox:latest}). */
    private String dockerImage = "algoverse/sandbox:latest";

    /**
     * OCI runtime passed to {@code docker run --runtime}.
     * Defaults to {@code runsc} (gVisor) for kernel-level isolation.
     */
    private String runtime = "runsc";

    /** Hard memory cap for the container, in megabytes. */
    private int memoryLimitMb = 256;

    /** CPU fraction (e.g. {@code "0.5"} = half a core). */
    private String cpuLimit = "0.5";

    /** Maximum wall-clock seconds the sandbox may run before SIGKILL. */
    private int timeoutSeconds = 10;

    /** When true, {@code --network=none} is appended to the Docker command. */
    private boolean networkDisabled = true;
}

package com.algoverse.execution.application.usecase;

import com.algoverse.execution.application.dto.RunRequest;
import com.algoverse.execution.application.dto.RunResultDto;
import com.algoverse.execution.domain.exception.QuotaExceededException;
import com.algoverse.execution.infrastructure.sandbox.ExecutionResult;
import com.algoverse.execution.infrastructure.sandbox.SandboxConfig;
import com.algoverse.execution.infrastructure.sandbox.SandboxRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Executes user code against a custom input without persisting a submission.
 *
 * <p>Uses a separate, lighter Redis quota ({@code execution.run-quota-per-user-per-hour})
 * so quick "run" iterations don't burn into the submission judge quota.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunCodeUseCase {

    private static final String QUOTA_KEY_PREFIX = "quota:run:";
    private static final Duration QUOTA_WINDOW    = Duration.ofHours(1);

    private final SandboxRunner       sandboxRunner;
    private final SandboxConfig       sandboxConfig;
    private final StringRedisTemplate redisTemplate;

    @Value("${execution.run-quota-per-user-per-hour:60}")
    private int runQuotaPerUserPerHour;

    /**
     * Validates the run quota, delegates to the sandbox, and returns the result.
     *
     * @param req    Validated run request with language, code, and custom input.
     * @param userId Authenticated user UUID — used for quota tracking.
     * @return Run result DTO with stdout, stderr, runtime, and success flag.
     */
    public RunResultDto execute(RunRequest req, UUID userId) {
        // Quota check
        checkQuota(userId);
        incrementQuota(userId);

        log.debug("Running code: language={} userId={} inputLength={}",
                req.language(), userId,
                req.customInput() == null ? 0 : req.customInput().length());

        ExecutionResult result = sandboxRunner.run(
                req.code(),
                req.language(),
                req.customInput(),
                sandboxConfig
        );

        log.debug("Run complete: language={} userId={} exitCode={} runtimeMs={} timedOut={}",
                req.language(), userId, result.exitCode(), result.runtimeMs(), result.timedOut());

        return new RunResultDto(
                result.stdout(),
                result.stderr(),
                result.runtimeMs(),
                result.isSuccess()
        );
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void checkQuota(UUID userId) {
        String key = QUOTA_KEY_PREFIX + userId;
        String raw = redisTemplate.opsForValue().get(key);
        if (raw != null && Long.parseLong(raw) >= runQuotaPerUserPerHour) {
            throw new QuotaExceededException(
                    "Run quota exceeded: " + runQuotaPerUserPerHour
                            + " runs per hour. Try again later.");
        }
    }

    private void incrementQuota(UUID userId) {
        String key = QUOTA_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, QUOTA_WINDOW);
        }
    }
}

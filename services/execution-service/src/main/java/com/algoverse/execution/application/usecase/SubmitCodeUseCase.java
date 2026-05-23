package com.algoverse.execution.application.usecase;

import com.algoverse.execution.application.dto.*;
import com.algoverse.execution.domain.JudgeResult;
import com.algoverse.execution.domain.exception.QuotaExceededException;
import com.algoverse.execution.domain.model.*;
import com.algoverse.execution.domain.repository.SubmissionRepository;
import com.algoverse.execution.domain.repository.SubmissionTestResultRepository;
import com.algoverse.execution.infrastructure.client.ProblemServiceClient;
import com.algoverse.execution.infrastructure.sandbox.ExecutionResult;
import com.algoverse.execution.infrastructure.sandbox.SandboxConfig;
import com.algoverse.execution.infrastructure.sandbox.SandboxRunner;
import com.algoverse.execution.infrastructure.websocket.SubmissionWebSocketService;
import com.algoverse.execution.kafka.SubmissionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates the full code submission lifecycle.
 *
 * <p>The public {@link #execute} method is synchronous — it persists the
 * submission and returns immediately (HTTP 202). The private
 * {@link #judgeAsync} method runs in the {@code judgingExecutor} thread pool
 * and handles the async judge pipeline: sandbox execution, test-case
 * comparison, WebSocket streaming, and Kafka publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitCodeUseCase {

    private static final String QUOTA_KEY_PREFIX = "quota:submit:";
    private static final Duration QUOTA_WINDOW    = Duration.ofHours(1);

    private final SubmissionRepository           submissionRepository;
    private final SubmissionTestResultRepository testResultRepository;
    private final ProblemServiceClient           problemServiceClient;
    private final SandboxRunner                  sandboxRunner;
    private final SandboxConfig                  sandboxConfig;
    private final SubmissionWebSocketService     webSocketService;
    private final SubmissionEventProducer        eventProducer;
    private final StringRedisTemplate            redisTemplate;

    @Value("${execution.quota-per-user-per-hour:30}")
    private int quotaPerUserPerHour;

    // -----------------------------------------------------------------------
    // Public entry point (synchronous, returns 202)
    // -----------------------------------------------------------------------

    /**
     * Validates quota, persists the submission as PENDING, increments the Redis
     * quota counter, and fires the async judge pipeline. Returns immediately.
     *
     * @param req    Validated submit request.
     * @param userId Authenticated user UUID.
     * @return SubmissionDto with status PENDING.
     */
    @Transactional
    public SubmissionDto execute(SubmitRequest req, UUID userId) {
        // 1. Quota check via Redis
        checkQuota(userId);

        // 2. Fetch problem meta for denormalized fields (slug, difficulty)
        ProblemMetaDto meta = fetchMetaSafely(req.problemId());

        // 3. Create and persist submission
        com.algoverse.execution.domain.model.Submission submission =
                com.algoverse.execution.domain.model.Submission.builder()
                        .userId(userId)
                        .problemId(req.problemId())
                        .problemSlug(meta != null ? meta.slug() : null)
                        .language(req.language())
                        .code(req.code())
                        .status(SubmissionStatus.PENDING)
                        .difficulty(parseDifficulty(meta))
                        .build();

        submission = submissionRepository.save(submission);
        UUID submissionId = submission.getIdAsUuid();

        // 4. Increment Redis quota counter (atomic INCR + EXPIRE)
        incrementQuota(userId);

        // 5. Publish submission.created event
        String eventId = UUID.randomUUID().toString();
        submission.setCreatedEventId(eventId);
        submissionRepository.save(submission); // persist createdEventId

        int queuePosition = (int) submissionRepository.countByUserIdAndSubmittedAtAfter(
                userId, Instant.now().minus(QUOTA_WINDOW));

        eventProducer.sendSubmissionCreated(toProducerDomain(submission), queuePosition);

        // 6. Send WebSocket STATUS_UPDATE(PENDING)
        webSocketService.sendSubmissionUpdate(userId,
                WebSocketMessage.statusUpdate(submissionId, Map.of("status", SubmissionStatus.PENDING)));

        // 7. Kick off async judging (non-blocking)
        judgeAsync(submissionId, userId, req.language(), req.code(), meta);

        return toDto(submission);
    }

    // -----------------------------------------------------------------------
    // Async judge pipeline
    // -----------------------------------------------------------------------

    @Async("judgingExecutor")
    public void judgeAsync(UUID submissionId, UUID userId,
                           Language language, String code, ProblemMetaDto meta) {
        long pipelineStart = System.currentTimeMillis();

        com.algoverse.execution.domain.model.Submission submission =
                submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.error("Submission not found for async judging: id={}", submissionId);
            return;
        }

        try {
            // 1. Update to RUNNING
            submission.setStatus(SubmissionStatus.RUNNING);
            submissionRepository.save(submission);
            webSocketService.sendSubmissionUpdate(userId,
                    WebSocketMessage.statusUpdate(submissionId, Map.of("status", SubmissionStatus.RUNNING)));

            // 2. Fetch test cases
            List<TestCaseDto> testCases = problemServiceClient.getTestCases(submission.getProblemIdAsUuid());
            int total = testCases.size();

            // 3. Run each test case
            int passed = 0;
            int maxRuntimeMs = 0;
            int maxMemoryMb = 0;
            SubmissionStatus finalStatus = SubmissionStatus.ACCEPTED;
            String firstErrorMessage = null;

            for (int i = 0; i < testCases.size(); i++) {
                TestCaseDto tc = testCases.get(i);

                ExecutionResult result = sandboxRunner.run(code, language, tc.input(), sandboxConfig);

                boolean ok = evaluateResult(result, tc.expectedOutput());

                String tcStatus = deriveTestCaseStatus(result, ok);
                int tcRuntime = (int) Math.min(result.runtimeMs(), Integer.MAX_VALUE);
                // Memory not directly measured by CLI runner; placeholder via sandbox config
                int tcMemory = 0;

                // Persist test result
                SubmissionTestResult testResult = SubmissionTestResult.builder()
                        .submissionId(submissionId)
                        .testCaseId(tc.id())
                        .status(tcStatus)
                        .actualOutput(truncate(result.stdout(), 4096))
                        .runtimeMs(tcRuntime)
                        .memoryMb(tcMemory)
                        .errorMessage(result.timedOut() ? "Time limit exceeded"
                                : (!ok && !result.stderr().isBlank()) ? truncate(result.stderr(), 2048) : null)
                        .orderIndex(i)
                        .build();
                testResultRepository.save(testResult);

                // Stream test result over WebSocket
                webSocketService.sendSubmissionUpdate(userId,
                        WebSocketMessage.testResult(submissionId, Map.of(
                                "testCaseId", tc.id(),
                                "passed", ok,
                                "runtime", tcRuntime,
                                "output", truncate(result.stdout(), 512),
                                "index", i
                        )));

                if (ok) {
                    passed++;
                } else if (finalStatus == SubmissionStatus.ACCEPTED) {
                    // First failure sets the final verdict
                    finalStatus = mapToStatus(result);
                    firstErrorMessage = buildErrorMessage(result);
                }

                maxRuntimeMs = Math.max(maxRuntimeMs, tcRuntime);
                maxMemoryMb  = Math.max(maxMemoryMb, tcMemory);
            }

            // 4. Update submission with final verdict
            submission.setStatus(finalStatus);
            submission.setRuntimeMs(maxRuntimeMs);
            submission.setMemoryMb(maxMemoryMb);
            submission.setTestCasesPassed(passed);
            submission.setTestCasesTotal(total);
            submission.setErrorMessage(firstErrorMessage);
            submissionRepository.save(submission);

            // 5. Send COMPLETE WebSocket message
            webSocketService.sendSubmissionUpdate(userId,
                    WebSocketMessage.complete(submissionId, Map.of(
                            "status", finalStatus,
                            "runtime", maxRuntimeMs,
                            "passedTestCases", passed,
                            "totalTestCases", total
                    )));

            // 6. Publish submission.judged Kafka event
            long totalMs = System.currentTimeMillis() - pipelineStart;
            JudgeResult judgeResult = JudgeResult.builder()
                    .status(finalStatus)
                    .runtimeMs(maxRuntimeMs)
                    .memoryMb(maxMemoryMb)
                    .testCasesPassed(passed)
                    .testCasesTotal(total)
                    .totalProcessingMs(totalMs)
                    .build();

            eventProducer.sendSubmissionJudged(toProducerDomain(submission), judgeResult);

            log.info("Judging complete: submissionId={} status={} passed={}/{} runtimeMs={} totalMs={}",
                    submissionId, finalStatus, passed, total, maxRuntimeMs, totalMs);

        } catch (Exception e) {
            log.error("Judging pipeline failed for submissionId={}: {}", submissionId, e.getMessage(), e);

            try {
                submission.setStatus(SubmissionStatus.SYSTEM_ERROR);
                submission.setErrorMessage("Internal judging error: " + e.getMessage());
                submissionRepository.save(submission);

                webSocketService.sendSubmissionUpdate(userId,
                        WebSocketMessage.error(submissionId, Map.of(
                                "message", "Judging failed due to a system error")));
            } catch (Exception inner) {
                log.error("Failed to persist SYSTEM_ERROR for submissionId={}", submissionId, inner);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void checkQuota(UUID userId) {
        String key = QUOTA_KEY_PREFIX + userId;
        String raw = redisTemplate.opsForValue().get(key);
        if (raw != null && Long.parseLong(raw) >= quotaPerUserPerHour) {
            throw new QuotaExceededException(
                    "Submission quota exceeded: " + quotaPerUserPerHour
                            + " submissions per hour. Try again later.");
        }
    }

    private void incrementQuota(UUID userId) {
        String key = QUOTA_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First increment — set expiry for the sliding 1-hour window
            redisTemplate.expire(key, QUOTA_WINDOW);
        }
    }

    private ProblemMetaDto fetchMetaSafely(UUID problemId) {
        try {
            return problemServiceClient.getProblemMeta(problemId);
        } catch (Exception e) {
            log.warn("Failed to fetch problem meta for problemId={}: {}", problemId, e.getMessage());
            return null;
        }
    }

    private static boolean evaluateResult(ExecutionResult result, String expectedOutput) {
        if (!result.isSuccess()) return false;
        String actual   = result.stdout()  == null ? "" : result.stdout().trim();
        String expected = expectedOutput    == null ? "" : expectedOutput.trim();
        return actual.equals(expected);
    }

    private static String deriveTestCaseStatus(ExecutionResult result, boolean passed) {
        if (passed)              return "PASSED";
        if (result.timedOut())   return "TLE";
        if (result.exitCode() != 0) return "RE";
        return "FAILED";
    }

    private static SubmissionStatus mapToStatus(ExecutionResult result) {
        if (result.timedOut() || result.exitCode() == 124) return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        if (result.exitCode() != 0) return SubmissionStatus.RUNTIME_ERROR;
        return SubmissionStatus.WRONG_ANSWER;
    }

    private static String buildErrorMessage(ExecutionResult result) {
        if (result.timedOut())             return "Time limit exceeded";
        if (!result.stderr().isBlank())    return truncate(result.stderr(), 2048);
        return "Wrong answer";
    }

    private static Difficulty parseDifficulty(ProblemMetaDto meta) {
        if (meta == null || meta.difficulty() == null) return Difficulty.MEDIUM;
        try {
            return Difficulty.valueOf(meta.difficulty().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Difficulty.MEDIUM;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /**
     * Converts the JPA entity to the {@code com.algoverse.execution.domain.Submission}
     * proxy type that {@link SubmissionEventProducer} expects.
     */
    private com.algoverse.execution.domain.model.Submission toProducerDomain(
            com.algoverse.execution.domain.model.Submission entity) {
        // The entity IS the domain object (Submission extends the model Submission).
        return entity;
    }

    private static SubmissionDto toDto(com.algoverse.execution.domain.model.Submission s) {
        return new SubmissionDto(
                s.getIdAsUuid(),
                s.getUserIdAsUuid(),
                s.getProblemIdAsUuid(),
                s.getLanguage(),
                s.getStatus(),
                s.getRuntimeMs(),
                s.getMemoryMb(),
                s.getTestCasesPassed(),
                s.getTestCasesTotal(),
                s.getErrorMessage(),
                s.getSubmittedAt()
        );
    }
}

package com.algoverse.submission.service;

import com.algoverse.submission.domain.Submission;
import com.algoverse.submission.domain.SubmissionStatus;
import com.algoverse.submission.dto.SubmissionResult;
import com.algoverse.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock judge service that simulates code execution.
 *
 * <p>Real AST parsing and deep analysis is delegated to ai-service.
 * This judge provides a realistic probabilistic outcome useful for
 * demo and development purposes.
 *
 * <p>Outcome weights (when code does not contain obvious correct patterns):
 * <ul>
 *   <li>60% ACCEPTED</li>
 *   <li>20% WRONG_ANSWER</li>
 *   <li>10% TIME_LIMIT_EXCEEDED</li>
 *   <li>10% RUNTIME_ERROR</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeService {

    private final SubmissionRepository submissionRepository;

    private static final int TOTAL_TEST_CASES = 10;

    /**
     * Judges the submission by simulating execution.
     *
     * @param submission the submission entity (must be RUNNING at call time)
     * @return a fully populated SubmissionResult
     */
    public SubmissionResult judge(Submission submission) {
        simulateExecutionDelay();

        SubmissionStatus status = determineStatus(submission);
        int executionTimeMs  = ThreadLocalRandom.current().nextInt(50, 801);
        int memoryUsedKb     = ThreadLocalRandom.current().nextInt(2000, 16001);
        int passedTestCases  = computePassedTests(status);
        String errorMessage  = buildErrorMessage(status);
        boolean isFirstSolve = computeIsFirstSolve(submission, status);

        log.debug("JudgeService: submission={} status={} exec={}ms mem={}kb firstSolve={}",
                submission.getId(), status, executionTimeMs, memoryUsedKb, isFirstSolve);

        return new SubmissionResult(
                submission.getId(),
                submission.getUserId(),
                submission.getProblemSlug(),
                submission.getLanguage(),
                status,
                submission.getDifficulty(),
                executionTimeMs,
                memoryUsedKb,
                passedTestCases,
                TOTAL_TEST_CASES,
                errorMessage,
                isFirstSolve,
                submission.getSubmittedAt(),
                OffsetDateTime.now()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void simulateExecutionDelay() {
        try {
            long delay = ThreadLocalRandom.current().nextLong(200, 1501);
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("JudgeService: execution delay interrupted");
        }
    }

    /**
     * Determines outcome. If the code contains well-known correct patterns for
     * common problems, always returns ACCEPTED. Otherwise uses weighted random.
     */
    private SubmissionStatus determineStatus(Submission submission) {
        if (isObviouslyCorrect(submission.getCode(), submission.getProblemSlug())) {
            return SubmissionStatus.ACCEPTED;
        }
        return weightedRandom();
    }

    /**
     * Checks for common correct solution patterns for well-known problems.
     * This is a heuristic — detailed analysis belongs in ai-service.
     */
    private boolean isObviouslyCorrect(String code, String slug) {
        if (code == null) return false;
        String normalised = code.toLowerCase();

        return switch (slug) {
            case "two-sum" ->
                    normalised.contains("hashmap") || normalised.contains("hash_map") ||
                    normalised.contains("{}") || normalised.contains("dict()");
            case "reverse-linked-list" ->
                    normalised.contains("prev") && normalised.contains("next");
            case "valid-parentheses" ->
                    normalised.contains("stack") &&
                    (normalised.contains("push") || normalised.contains("append"));
            case "fibonacci" ->
                    normalised.contains("memo") || normalised.contains("dp");
            case "binary-search" ->
                    normalised.contains("mid") && normalised.contains("left") &&
                    normalised.contains("right");
            default -> false;
        };
    }

    /** Weighted random outcome according to spec distribution. */
    private SubmissionStatus weightedRandom() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60)  return SubmissionStatus.ACCEPTED;
        if (roll < 80)  return SubmissionStatus.WRONG_ANSWER;
        if (roll < 90)  return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        return           SubmissionStatus.RUNTIME_ERROR;
    }

    private int computePassedTests(SubmissionStatus status) {
        return switch (status) {
            case ACCEPTED -> TOTAL_TEST_CASES;
            case WRONG_ANSWER -> ThreadLocalRandom.current().nextInt(0, TOTAL_TEST_CASES);
            case TIME_LIMIT_EXCEEDED -> ThreadLocalRandom.current().nextInt(0, TOTAL_TEST_CASES / 2);
            case RUNTIME_ERROR, COMPILATION_ERROR -> 0;
            default -> 0;
        };
    }

    private String buildErrorMessage(SubmissionStatus status) {
        return switch (status) {
            case WRONG_ANSWER        -> "Expected output does not match for one or more test cases.";
            case TIME_LIMIT_EXCEEDED -> "Execution exceeded the 2000ms time limit on test case #5.";
            case RUNTIME_ERROR       -> "NullPointerException at line 7 during test case #3 execution.";
            case COMPILATION_ERROR   -> "Compilation failed: unexpected token near line 3.";
            default                  -> null;
        };
    }

    /**
     * Determines isFirstSolve: true only if this is an ACCEPTED submission
     * and no prior ACCEPTED submission exists for this user + problem.
     */
    private boolean computeIsFirstSolve(Submission submission, SubmissionStatus status) {
        if (status != SubmissionStatus.ACCEPTED) {
            return false;
        }
        boolean hasPriorAccepted = submissionRepository.existsByUserIdAndProblemSlugAndStatus(
                submission.getUserId(),
                submission.getProblemSlug(),
                SubmissionStatus.ACCEPTED
        );
        return !hasPriorAccepted;
    }
}

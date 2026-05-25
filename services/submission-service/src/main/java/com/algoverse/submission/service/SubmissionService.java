package com.algoverse.submission.service;

import com.algoverse.submission.domain.Submission;
import com.algoverse.submission.domain.SubmissionStatus;
import com.algoverse.submission.dto.*;
import com.algoverse.submission.kafka.SubmissionEventProducer;
import com.algoverse.submission.repository.SubmissionRepository;
import com.algoverse.submission.websocket.SubmissionWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core business logic for submission lifecycle management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final JudgeService judgeService;
    private final RateLimitService rateLimitService;
    private final SubmissionEventProducer eventProducer;
    private final SubmissionWebSocketHandler webSocketHandler;

    // ── Submit ────────────────────────────────────────────────────────────────

    /**
     * Accepts a new submission, enforces rate limiting, persists with PENDING status,
     * and queues async grading. Returns immediately with a lightweight response.
     */
    @Transactional
    public SubmissionResponse submit(SubmitRequest request, UUID userId) {
        rateLimitService.checkAndIncrement(userId);

        Submission submission = Submission.builder()
                .userId(userId)
                .problemSlug(request.problemSlug())
                .language(request.language())
                .code(request.code())
                .difficulty(request.difficulty())
                .status(SubmissionStatus.PENDING)
                .submittedAt(OffsetDateTime.now())
                .build();

        submission = submissionRepository.save(submission);
        log.info("SubmissionService: saved submission id={} user={} problem={}",
                submission.getId(), userId, request.problemSlug());

        gradeAsync(submission.getId());

        return toResponse(submission);
    }

    // ── Async grading ─────────────────────────────────────────────────────────

    /**
     * Asynchronously grades a submission:
     * <ol>
     *   <li>Marks submission as RUNNING</li>
     *   <li>Calls the mock judge</li>
     *   <li>Persists the result</li>
     *   <li>Pushes result via WebSocket</li>
     *   <li>Publishes Kafka event for ACCEPTED submissions</li>
     * </ol>
     */
    @Async("submissionExecutor")
    public CompletableFuture<Void> gradeAsync(UUID submissionId) {
        try {
            // 1. Load & transition to RUNNING
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Submission not found: " + submissionId));

            submission.setStatus(SubmissionStatus.RUNNING);
            submissionRepository.save(submission);
            log.debug("SubmissionService: grading started submissionId={}", submissionId);

            // 2. Run mock judge
            SubmissionResult result = judgeService.judge(submission);

            // 3. Persist result
            submission.setStatus(result.status());
            submission.setExecutionTimeMs(result.executionTimeMs());
            submission.setMemoryUsedKb(result.memoryUsedKb());
            submission.setPassedTestCases(result.passedTestCases());
            submission.setTotalTestCases(result.totalTestCases());
            submission.setErrorMessage(result.errorMessage());
            submission.setIsFirstSolve(result.isFirstSolve());
            submission.setGradedAt(result.gradedAt());
            submissionRepository.save(submission);

            log.info("SubmissionService: grading complete submissionId={} status={}",
                    submissionId, result.status());

            // 4. Push real-time result via WebSocket
            webSocketHandler.sendResult(submissionId, result);

            // 5. Publish Kafka event for ACCEPTED
            if (result.status() == SubmissionStatus.ACCEPTED) {
                GradedEvent event = new GradedEvent(
                        submission.getId(),
                        submission.getUserId(),
                        submission.getProblemSlug(),
                        submission.getDifficulty(),
                        result.status().name(),
                        result.executionTimeMs() != null ? result.executionTimeMs() : 0,
                        result.isFirstSolve() != null && result.isFirstSolve(),
                        result.gradedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                );
                eventProducer.publishGradedEvent(event);
            }

        } catch (Exception ex) {
            log.error("SubmissionService: grading failed for submissionId={}: {}",
                    submissionId, ex.getMessage(), ex);
            // Mark as RUNTIME_ERROR so the submission is not stuck in RUNNING
            submissionRepository.findById(submissionId).ifPresent(s -> {
                s.setStatus(SubmissionStatus.RUNTIME_ERROR);
                s.setErrorMessage("Internal grading error: " + ex.getMessage());
                s.setGradedAt(OffsetDateTime.now());
                submissionRepository.save(s);
            });
        }

        return CompletableFuture.completedFuture(null);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns the full graded result for a submission.
     * Enforces that the requesting user owns the submission.
     */
    @Transactional(readOnly = true)
    public SubmissionResult getSubmission(UUID submissionId, UUID requestingUserId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Submission not found: " + submissionId));

        if (!submission.getUserId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you do not own this submission");
        }

        return toResult(submission);
    }

    /** Paginated submission history for the authenticated user. */
    @Transactional(readOnly = true)
    public Page<SubmissionResponse> getMySubmissions(UUID userId, Pageable pageable) {
        return submissionRepository
                .findByUserIdOrderBySubmittedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /** All user attempts on a specific problem. */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> getMySubmissionsForProblem(UUID userId, String problemSlug) {
        return submissionRepository
                .findByUserIdAndProblemSlugOrderBySubmittedAtDesc(userId, problemSlug)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private SubmissionResponse toResponse(Submission s) {
        return new SubmissionResponse(
                s.getId(),
                s.getProblemSlug(),
                s.getLanguage(),
                s.getStatus(),
                s.getDifficulty(),
                s.getSubmittedAt()
        );
    }

    private SubmissionResult toResult(Submission s) {
        return new SubmissionResult(
                s.getId(),
                s.getUserId(),
                s.getProblemSlug(),
                s.getLanguage(),
                s.getStatus(),
                s.getDifficulty(),
                s.getExecutionTimeMs(),
                s.getMemoryUsedKb(),
                s.getPassedTestCases(),
                s.getTotalTestCases(),
                s.getErrorMessage(),
                s.getIsFirstSolve(),
                s.getSubmittedAt(),
                s.getGradedAt()
        );
    }
}

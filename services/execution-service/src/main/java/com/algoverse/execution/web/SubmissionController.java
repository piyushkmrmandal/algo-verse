package com.algoverse.execution.web;

import com.algoverse.execution.application.dto.*;
import com.algoverse.execution.application.usecase.RunCodeUseCase;
import com.algoverse.execution.application.usecase.SubmitCodeUseCase;
import com.algoverse.execution.domain.exception.NotFoundException;
import com.algoverse.execution.domain.model.Submission;
import com.algoverse.execution.domain.model.SubmissionStatus;
import com.algoverse.execution.domain.repository.SubmissionRepository;
import com.algoverse.execution.infrastructure.security.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for code submission and execution.
 *
 * <p>All endpoints require a valid Bearer JWT. The userId is extracted from
 * the SecurityContext (set by {@link JwtAuthenticationFilter}).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@Tag(name = "Submissions", description = "Code submission, judging, and run-against-custom-input APIs")
@SecurityRequirement(name = "BearerAuth")
public class SubmissionController {

    private final SubmitCodeUseCase    submitCodeUseCase;
    private final RunCodeUseCase       runCodeUseCase;
    private final SubmissionRepository submissionRepository;

    // -----------------------------------------------------------------------
    // POST /api/v1/submissions  — submit code for judging
    // -----------------------------------------------------------------------

    @Operation(
            summary = "Submit code for judging",
            description = "Persists the submission and starts async judging. Returns immediately "
                    + "with HTTP 202. Real-time updates streamed via WebSocket "
                    + "/user/{userId}/topic/submission.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Submission accepted for judging"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "429", description = "Submission quota exceeded")
            }
    )
    @PostMapping
    public ResponseEntity<SubmissionDto> submit(@Valid @RequestBody SubmitRequest req) {
        UUID userId = JwtAuthenticationFilter.extractUserId();
        log.info("Submit request: userId={} problemId={} language={}", userId, req.problemId(), req.language());
        SubmissionDto dto = submitCodeUseCase.execute(req, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/submissions/run  — run code against custom input
    // -----------------------------------------------------------------------

    @Operation(
            summary = "Run code against custom input",
            description = "Executes code in the sandbox without persisting a submission. "
                    + "Uses a separate, lighter rate limit (60 runs/hour vs 30 submissions/hour).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Execution result"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "429", description = "Run quota exceeded")
            }
    )
    @PostMapping("/run")
    public ResponseEntity<RunResultDto> run(@Valid @RequestBody RunRequest req) {
        UUID userId = JwtAuthenticationFilter.extractUserId();
        log.debug("Run request: userId={} language={}", userId, req.language());
        RunResultDto result = runCodeUseCase.execute(req, userId);
        return ResponseEntity.ok(result);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/submissions  — list user's submissions (paginated)
    // -----------------------------------------------------------------------

    @Operation(
            summary = "List my submissions",
            description = "Returns a paginated list of the authenticated user's submissions, "
                    + "newest first. Optionally filtered by problemId."
    )
    @GetMapping
    public ResponseEntity<Page<SubmissionDto>> listMySubmissions(
            @Parameter(description = "Filter by problem UUID")
            @RequestParam(required = false) UUID problemId,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = JwtAuthenticationFilter.extractUserId();
        int clampedSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, clampedSize,
                Sort.by(Sort.Direction.DESC, "submittedAt"));

        Page<Submission> entities = (problemId != null)
                ? submissionRepository.findByUserIdAndProblemId(userId, problemId, pageable)
                : submissionRepository.findByUserId(userId, pageable);

        return ResponseEntity.ok(entities.map(SubmissionController::toDto));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/submissions/{id}  — get single submission
    // -----------------------------------------------------------------------

    @Operation(
            summary = "Get submission by ID",
            description = "Returns a single submission. Users can only access their own submissions.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Submission found"),
                    @ApiResponse(responseCode = "404", description = "Submission not found"),
                    @ApiResponse(responseCode = "403", description = "Access denied — not owner")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDto> getSubmission(@PathVariable UUID id) {
        UUID userId = JwtAuthenticationFilter.extractUserId();

        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + id));

        // Ownership check — users can only see their own submissions
        if (!submission.getUserIdAsUuid().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(toDto(submission));
    }

    // -----------------------------------------------------------------------
    // Private mapper
    // -----------------------------------------------------------------------

    private static SubmissionDto toDto(Submission s) {
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

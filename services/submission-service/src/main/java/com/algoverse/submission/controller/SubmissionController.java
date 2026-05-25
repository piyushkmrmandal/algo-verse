package com.algoverse.submission.controller;

import com.algoverse.submission.dto.*;
import com.algoverse.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the submission lifecycle.
 *
 * <p>All endpoints require a valid JWT in the Authorization header.
 */
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Submissions", description = "Code submission and grading APIs")
@SecurityRequirement(name = "bearerAuth")
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * POST /api/v1/submissions
     *
     * <p>Submits code for grading. Applies rate limiting (10/min per user).
     * Returns immediately with status=PENDING; the final result is pushed
     * via WebSocket to /topic/submissions/{submissionId}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit code for grading",
               description = "Accepts code submission, queues async grading, returns PENDING response immediately.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Submission accepted"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public SubmissionResponse submit(
            @Valid @RequestBody SubmitRequest request,
            @AuthenticationPrincipal UUID userId) {
        log.info("SubmissionController: POST /api/v1/submissions user={} problem={}",
                userId, request.problemSlug());
        return submissionService.submit(request, userId);
    }

    /**
     * GET /api/v1/submissions/{id}
     *
     * <p>Polls the graded result for a specific submission.
     * Returns 403 if the submission does not belong to the authenticated user.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get submission result by ID",
               description = "Returns the full graded result. 403 if not owned by the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission result"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not owner"),
            @ApiResponse(responseCode = "404", description = "Submission not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public SubmissionResult getSubmission(
            @Parameter(description = "Submission UUID") @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        log.debug("SubmissionController: GET /api/v1/submissions/{} user={}", id, userId);
        return submissionService.getSubmission(id, userId);
    }

    /**
     * GET /api/v1/submissions/me
     *
     * <p>Returns a paginated history of the authenticated user's submissions.
     */
    @GetMapping("/me")
    @Operation(summary = "Get authenticated user's submission history",
               description = "Paginated list of all submissions for the calling user.")
    public Page<SubmissionResponse> getMySubmissions(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 20, sort = "submittedAt") Pageable pageable) {
        log.debug("SubmissionController: GET /api/v1/submissions/me user={}", userId);
        return submissionService.getMySubmissions(userId, pageable);
    }

    /**
     * GET /api/v1/submissions/problem/{slug}/me
     *
     * <p>Returns all attempts by the authenticated user on a specific problem.
     */
    @GetMapping("/problem/{slug}/me")
    @Operation(summary = "Get user's attempts on a problem",
               description = "All submissions by the calling user for a specific problem slug.")
    public List<SubmissionResponse> getMySubmissionsForProblem(
            @Parameter(description = "Problem slug, e.g. two-sum") @PathVariable String slug,
            @AuthenticationPrincipal UUID userId) {
        log.debug("SubmissionController: GET /api/v1/submissions/problem/{}/me user={}", slug, userId);
        return submissionService.getMySubmissionsForProblem(userId, slug);
    }
}

package com.algoverse.problem.web;

import com.algoverse.problem.application.dto.*;
import com.algoverse.problem.application.usecase.CreateProblemUseCase;
import com.algoverse.problem.application.usecase.GetProblemUseCase;
import com.algoverse.problem.application.usecase.GetProblemsUseCase;
import com.algoverse.problem.domain.model.Topic;
import com.algoverse.problem.domain.repository.TopicRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
@Tag(name = "Problems", description = "DSA Problem catalog endpoints")
public class ProblemController {

    private final GetProblemsUseCase getProblemsUseCase;
    private final GetProblemUseCase getProblemUseCase;
    private final CreateProblemUseCase createProblemUseCase;
    private final TopicRepository topicRepository;

    @GetMapping
    @Operation(
            summary = "List problems",
            description = "Returns a paginated list of DSA problems with optional filters"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Problems retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters")
    })
    public ResponseEntity<ProblemPageDto> getProblems(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Filter by difficulty: EASY, MEDIUM, HARD")
            @RequestParam(required = false) String difficulty,

            @Parameter(description = "Filter by topic slug")
            @RequestParam(required = false) String topic,

            @Parameter(description = "Search by title keyword")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filter premium problems")
            @RequestParam(required = false) Boolean premium
    ) {
        ProblemPageDto result = getProblemsUseCase.execute(page, size, difficulty, topic, search, premium);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{slug}")
    @Operation(
            summary = "Get problem by slug",
            description = "Returns full problem detail including sample test cases"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Problem found"),
            @ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ResponseEntity<ProblemDetailDto> getProblem(
            @Parameter(description = "Unique problem slug", example = "two-sum")
            @PathVariable String slug
    ) {
        ProblemDetailDto result = getProblemUseCase.execute(slug);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new problem",
            description = "Admin-only: creates a new DSA problem in the catalog",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Problem created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "409", description = "Problem with slug or title already exists")
    })
    public ResponseEntity<ProblemDetailDto> createProblem(
            @Valid @RequestBody CreateProblemRequest request
    ) {
        ProblemDetailDto created = createProblemUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/topics")
    @Operation(
            summary = "List all topics",
            description = "Returns all available DSA topics / categories"
    )
    @ApiResponse(responseCode = "200", description = "Topics retrieved successfully")
    public ResponseEntity<List<TopicDto>> getTopics() {
        List<TopicDto> topics = topicRepository.findAll().stream()
                .map(t -> new TopicDto(t.getId(), t.getName(), t.getSlug(), t.getDescription()))
                .toList();
        return ResponseEntity.ok(topics);
    }
}

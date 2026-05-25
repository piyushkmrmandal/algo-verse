package com.algoverse.sysdesign.controller;

import com.algoverse.sysdesign.dto.*;
import com.algoverse.sysdesign.service.AiFeedbackService;
import com.algoverse.sysdesign.service.DiagramService;
import com.algoverse.sysdesign.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sysdesign")
@RequiredArgsConstructor
@Tag(name = "System Design", description = "System Design interview preparation endpoints")
public class SysdesignController {

    private final ProblemService problemService;
    private final DiagramService diagramService;
    private final AiFeedbackService aiFeedbackService;

    // ─────────────────────────────────────────────────────────────
    // Problems
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/problems")
    @Operation(summary = "List system design problems", description = "Paginated, filterable by category")
    public ResponseEntity<Page<ProblemSummary>> getProblems(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(problemService.getProblems(category, pageable));
    }

    @GetMapping("/problems/{slug}")
    @Operation(summary = "Get problem detail by slug")
    public ResponseEntity<ProblemDetail> getProblem(@PathVariable String slug) {
        return ResponseEntity.ok(problemService.getProblemBySlug(slug));
    }

    // ─────────────────────────────────────────────────────────────
    // Diagrams
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/diagrams/me")
    @Operation(summary = "Get current user's saved diagrams",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<DiagramDto>> getMyDiagrams(Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(diagramService.getUserDiagrams(userId));
    }

    @GetMapping("/diagrams/{diagramId}")
    @Operation(summary = "Get a specific diagram with content",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DiagramDto> getDiagram(
            @PathVariable UUID diagramId,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(diagramService.getDiagram(diagramId, userId));
    }

    @PostMapping("/diagrams")
    @Operation(summary = "Save or update a diagram",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DiagramDto> saveDiagram(
            @Valid @RequestBody SaveDiagramRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(diagramService.saveDiagram(userId, request));
    }

    @PostMapping("/diagrams/{diagramId}/review")
    @Operation(summary = "Request AI feedback on a diagram",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DiagramFeedback> reviewDiagram(
            @PathVariable UUID diagramId,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(aiFeedbackService.reviewDiagram(diagramId, userId));
    }

    @DeleteMapping("/diagrams/{diagramId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a diagram",
               security = @SecurityRequirement(name = "bearerAuth"))
    public void deleteDiagram(
            @PathVariable UUID diagramId,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        diagramService.deleteDiagram(diagramId, userId);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private UUID extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}

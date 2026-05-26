package com.algoverse.submission.controller;

import com.algoverse.submission.domain.SubmissionStatus;
import com.algoverse.submission.dto.*;
import com.algoverse.submission.service.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SubmissionController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("SubmissionController WebMvc slice tests")
class SubmissionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SubmissionService submissionService;

    private static final UUID SUBMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID       = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SubmissionResponse pendingResponse() {
        return new SubmissionResponse(
                SUBMISSION_ID, "two-sum", "java",
                SubmissionStatus.PENDING, "EASY", OffsetDateTime.now()
        );
    }

    // ── POST /api/v1/submissions ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/submissions returns 202 ACCEPTED with PENDING response")
    void submit_validRequest_returns202() throws Exception {
        SubmitRequest request = new SubmitRequest("two-sum", "java", "// code", "EASY");
        when(submissionService.submit(any(SubmitRequest.class), nullable(UUID.class)))
                .thenReturn(pendingResponse());

        mockMvc.perform(post("/api/v1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.problemSlug", is("two-sum")))
                .andExpect(jsonPath("$.language", is("java")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.id", is(SUBMISSION_ID.toString())));
    }

    @Test
    @DisplayName("POST /api/v1/submissions with blank problemSlug returns 400")
    void submit_blankProblemSlug_returns400() throws Exception {
        String body = """
                {"problemSlug": "", "language": "java", "code": "// code", "difficulty": "EASY"}
                """;

        mockMvc.perform(post("/api/v1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionService);
    }

    @Test
    @DisplayName("POST /api/v1/submissions with invalid language returns 400")
    void submit_invalidLanguage_returns400() throws Exception {
        String body = """
                {"problemSlug": "two-sum", "language": "cobol", "code": "x", "difficulty": "EASY"}
                """;

        mockMvc.perform(post("/api/v1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/submissions rate limited returns 429")
    void submit_rateLimited_returns429() throws Exception {
        when(submissionService.submit(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));

        SubmitRequest request = new SubmitRequest("two-sum", "java", "// code", "EASY");

        mockMvc.perform(post("/api/v1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    // ── GET /api/v1/submissions/{id} ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/submissions/{id} returns 200 with graded result")
    void getSubmission_found_returns200() throws Exception {
        SubmissionResult result = new SubmissionResult(
                SUBMISSION_ID, USER_ID, "two-sum", "java",
                SubmissionStatus.ACCEPTED, "EASY",
                42, 4096, 10, 10, null, true,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
        when(submissionService.getSubmission(eq(SUBMISSION_ID), nullable(UUID.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/submissions/" + SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(SUBMISSION_ID.toString())))
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.executionTimeMs", is(42)))
                .andExpect(jsonPath("$.passedTestCases", is(10)))
                .andExpect(jsonPath("$.isFirstSolve", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/submissions/{id} returns 404 when not found")
    void getSubmission_notFound_returns404() throws Exception {
        when(submissionService.getSubmission(eq(SUBMISSION_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));

        mockMvc.perform(get("/api/v1/submissions/" + SUBMISSION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/submissions/{id} returns 403 for non-owner")
    void getSubmission_forbidden_returns403() throws Exception {
        when(submissionService.getSubmission(eq(SUBMISSION_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        mockMvc.perform(get("/api/v1/submissions/" + SUBMISSION_ID))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/submissions/me ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/submissions/me returns 200 paginated list")
    void getMySubmissions_returnsPaginatedList() throws Exception {
        List<SubmissionResponse> submissions = List.of(
                new SubmissionResponse(SUBMISSION_ID, "two-sum", "python",
                        SubmissionStatus.ACCEPTED, "EASY", OffsetDateTime.now()),
                new SubmissionResponse(UUID.randomUUID(), "binary-search", "java",
                        SubmissionStatus.WRONG_ANSWER, "MEDIUM", OffsetDateTime.now())
        );
        Page<SubmissionResponse> page = new PageImpl<>(submissions);
        when(submissionService.getMySubmissions(nullable(UUID.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/submissions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].problemSlug", is("two-sum")))
                .andExpect(jsonPath("$.content[0].status", is("ACCEPTED")))
                .andExpect(jsonPath("$.content[1].status", is("WRONG_ANSWER")));
    }

    @Test
    @DisplayName("GET /api/v1/submissions/me returns empty page when no submissions")
    void getMySubmissions_empty_returnsEmptyPage() throws Exception {
        when(submissionService.getMySubmissions(any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/submissions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ── GET /api/v1/submissions/problem/{slug}/me ─────────────────────────────

    @Test
    @DisplayName("GET /api/v1/submissions/problem/{slug}/me returns list for that problem")
    void getMySubmissionsForProblem_returnsList() throws Exception {
        List<SubmissionResponse> submissions = List.of(
                new SubmissionResponse(SUBMISSION_ID, "two-sum", "python",
                        SubmissionStatus.ACCEPTED, "EASY", OffsetDateTime.now())
        );
        when(submissionService.getMySubmissionsForProblem(any(), eq("two-sum")))
                .thenReturn(submissions);

        mockMvc.perform(get("/api/v1/submissions/problem/two-sum/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].problemSlug", is("two-sum")));
    }
}

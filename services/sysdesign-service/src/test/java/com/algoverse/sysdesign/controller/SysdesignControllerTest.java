package com.algoverse.sysdesign.controller;

import com.algoverse.sysdesign.domain.DiagramContent;
import com.algoverse.sysdesign.dto.*;
import com.algoverse.sysdesign.service.AiFeedbackService;
import com.algoverse.sysdesign.service.DiagramService;
import com.algoverse.sysdesign.service.ProblemService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SysdesignController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("SysdesignController WebMvc slice tests")
class SysdesignControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProblemService problemService;
    @MockBean DiagramService diagramService;
    @MockBean AiFeedbackService aiFeedbackService;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROB_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DIAG_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private org.springframework.test.web.servlet.request.RequestPostProcessor withUser(UUID userId) {
        return request -> {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId.toString(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            request.setUserPrincipal(auth);
            return request;
        };
    }

    // ── GET /api/v1/sysdesign/problems ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/sysdesign/problems returns 200 paginated list")
    void getProblems_returns200WithList() throws Exception {
        List<ProblemSummary> summaries = List.of(
                new ProblemSummary(PROB_ID, "design-twitter", "Design Twitter", "HARD", "Social", true),
                new ProblemSummary(UUID.randomUUID(), "design-redis", "Design Redis", "MEDIUM", "Storage", true)
        );
        when(problemService.getProblems(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(summaries));

        mockMvc.perform(get("/api/v1/sysdesign/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].slug", is("design-twitter")))
                .andExpect(jsonPath("$.content[0].difficulty", is("HARD")))
                .andExpect(jsonPath("$.content[1].slug", is("design-redis")));
    }

    @Test
    @DisplayName("GET /api/v1/sysdesign/problems?category=Storage filters by category")
    void getProblems_withCategory_filtersCorrectly() throws Exception {
        List<ProblemSummary> storageSummaries = List.of(
                new ProblemSummary(PROB_ID, "design-s3", "Design S3", "HARD", "Storage", true)
        );
        when(problemService.getProblems(eq("Storage"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(storageSummaries));

        mockMvc.perform(get("/api/v1/sysdesign/problems").param("category", "Storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].category", is("Storage")));

        verify(problemService).getProblems(eq("Storage"), any());
    }

    @Test
    @DisplayName("GET /api/v1/sysdesign/problems returns empty page when no problems")
    void getProblems_empty_returnsEmptyPage() throws Exception {
        when(problemService.getProblems(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/sysdesign/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ── GET /api/v1/sysdesign/problems/{slug} ────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/sysdesign/problems/{slug} returns 200 with problem detail")
    void getProblem_found_returns200() throws Exception {
        ProblemDetail detail = new ProblemDetail(
                PROB_ID, "design-twitter", "Design Twitter",
                "HARD", "Social",
                "## Design a Twitter-like system",
                List.of("Handle 100M users", "Support real-time timeline"),
                true, Instant.now()
        );
        when(problemService.getProblemBySlug("design-twitter")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/sysdesign/problems/design-twitter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", is("design-twitter")))
                .andExpect(jsonPath("$.difficulty", is("HARD")))
                .andExpect(jsonPath("$.category", is("Social")))
                .andExpect(jsonPath("$.requirements", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/sysdesign/problems/{slug} returns 404 for unknown slug")
    void getProblem_notFound_returns404() throws Exception {
        when(problemService.getProblemBySlug("nonexistent"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));

        mockMvc.perform(get("/api/v1/sysdesign/problems/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/sysdesign/diagrams/me ────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/sysdesign/diagrams/me returns user diagrams")
    void getMyDiagrams_returns200WithList() throws Exception {
        DiagramDto dto = new DiagramDto(DIAG_ID, USER_ID, PROB_ID,
                "My Solution", 2, false,
                Instant.now(), Instant.now(),
                List.of(), List.of(), Map.of());
        when(diagramService.getUserDiagrams(USER_ID)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/sysdesign/diagrams/me")
                        .with(withUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(DIAG_ID.toString())));
    }

    // ── POST /api/v1/sysdesign/diagrams ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/sysdesign/diagrams returns 201 with created diagram")
    void saveDiagram_validRequest_returns201() throws Exception {
        SaveDiagramRequest request = new SaveDiagramRequest(
                PROB_ID, "My Design", List.of(), List.of(), Map.of());
        DiagramDto dto = new DiagramDto(DIAG_ID, USER_ID, PROB_ID,
                "My Design", 1, false,
                Instant.now(), Instant.now(),
                List.of(), List.of(), Map.of());
        when(diagramService.saveDiagram(nullable(UUID.class), any(SaveDiagramRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/v1/sysdesign/diagrams")
                        .with(withUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(DIAG_ID.toString())))
                .andExpect(jsonPath("$.version", is(1)));
    }

    // ── POST /api/v1/sysdesign/diagrams/{diagramId}/review ───────────────────

    @Test
    @DisplayName("POST /api/v1/sysdesign/diagrams/{id}/review returns 200 with feedback")
    void reviewDiagram_returns200WithFeedback() throws Exception {
        DiagramFeedback feedback = new DiagramFeedback(
                "fb-1", DIAG_ID.toString(), 80, "Good design!",
                List.of("Clear separation of concerns"),
                List.of("Add load balancer"),
                List.of(), Instant.now()
        );
        when(aiFeedbackService.reviewDiagram(eq(DIAG_ID), nullable(UUID.class)))
                .thenReturn(feedback);

        mockMvc.perform(post("/api/v1/sysdesign/diagrams/" + DIAG_ID + "/review")
                        .with(withUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore", is(80)))
                .andExpect(jsonPath("$.summary", is("Good design!")))
                .andExpect(jsonPath("$.strengths", hasSize(1)))
                .andExpect(jsonPath("$.improvements", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/v1/sysdesign/diagrams/{id}/review returns 404 when diagram not found")
    void reviewDiagram_notFound_returns404() throws Exception {
        when(aiFeedbackService.reviewDiagram(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found"));

        mockMvc.perform(post("/api/v1/sysdesign/diagrams/" + DIAG_ID + "/review")
                        .with(withUser(USER_ID)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/v1/sysdesign/diagrams/{id} ────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/sysdesign/diagrams/{id} returns 204 No Content")
    void deleteDiagram_returns204() throws Exception {
        doNothing().when(diagramService).deleteDiagram(eq(DIAG_ID), nullable(UUID.class));

        mockMvc.perform(delete("/api/v1/sysdesign/diagrams/" + DIAG_ID)
                        .with(withUser(USER_ID)))
                .andExpect(status().isNoContent());

        verify(diagramService).deleteDiagram(eq(DIAG_ID), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/sysdesign/diagrams/{id} returns 403 for non-owner")
    void deleteDiagram_forbidden_returns403() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
                .when(diagramService).deleteDiagram(any(), any());

        mockMvc.perform(delete("/api/v1/sysdesign/diagrams/" + DIAG_ID)
                        .with(withUser(USER_ID)))
                .andExpect(status().isForbidden());
    }
}

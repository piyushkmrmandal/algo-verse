package com.algoverse.problem.web;

import com.algoverse.problem.application.dto.*;
import com.algoverse.problem.application.usecase.CreateProblemUseCase;
import com.algoverse.problem.application.usecase.GetProblemUseCase;
import com.algoverse.problem.application.usecase.GetProblemsUseCase;
import com.algoverse.problem.domain.exception.NotFoundException;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.repository.TopicRepository;
import com.algoverse.problem.infrastructure.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ProblemController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetProblemsUseCase getProblemsUseCase;

    @MockBean
    private GetProblemUseCase getProblemUseCase;

    @MockBean
    private CreateProblemUseCase createProblemUseCase;

    @MockBean
    private TopicRepository topicRepository;

    @Test
    @WithMockUser
    void getProblems_shouldReturn200WithPaginatedResults() throws Exception {
        ProblemSummaryDto summary = ProblemSummaryDto.from(
                UUID.randomUUID(), "two-sum", "Two Sum",
                Difficulty.EASY, List.of("array"), false, 1000, 750
        );

        ProblemPageDto page = new ProblemPageDto(List.of(summary), 1L, 1, 0, 20, false);
        when(getProblemsUseCase.execute(0, 20, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/problems")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].slug").value("two-sum"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @WithMockUser
    void getProblem_shouldReturn200WithDetail() throws Exception {
        ProblemDetailDto detail = new ProblemDetailDto(
                UUID.randomUUID(), "two-sum", "Two Sum", Difficulty.EASY,
                List.of("array"), false, 1000, 750, 75.0,
                "Given an array...", "1 <= n <= 10^4", "Array of integers",
                "Indices array", 1000, 256, List.of(), List.of("Arrays")
        );

        when(getProblemUseCase.execute("two-sum")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/problems/two-sum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("two-sum"))
                .andExpect(jsonPath("$.title").value("Two Sum"))
                .andExpect(jsonPath("$.acceptanceRate").value(75.0));
    }

    @Test
    @WithMockUser
    void getProblem_shouldReturn404WhenNotFound() throws Exception {
        when(getProblemUseCase.execute("unknown")).thenThrow(new NotFoundException("Problem", "slug", "unknown"));

        mockMvc.perform(get("/api/v1/problems/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProblem_shouldReturn201WhenAdminCreatesValidProblem() throws Exception {
        CreateProblemRequest request = new CreateProblemRequest(
                "new-problem", "New Problem", "Description",
                Difficulty.MEDIUM, 2000, 512,
                List.of("dp"), "1 <= n <= 1000", "Integer array", "Integer", false
        );

        ProblemDetailDto created = new ProblemDetailDto(
                UUID.randomUUID(), "new-problem", "New Problem", Difficulty.MEDIUM,
                List.of("dp"), false, 0, 0, 0.0,
                "Description", "1 <= n <= 1000", "Integer array", "Integer",
                2000, 512, List.of(), List.of()
        );

        when(createProblemUseCase.execute(any(CreateProblemRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/problems")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("new-problem"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createProblem_shouldReturn403WhenNotAdmin() throws Exception {
        CreateProblemRequest request = new CreateProblemRequest(
                "new-problem", "New Problem", "Description",
                Difficulty.MEDIUM, 2000, 512, null, null, null, null, false
        );

        mockMvc.perform(post("/api/v1/problems")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getTopics_shouldReturn200WithTopicList() throws Exception {
        when(topicRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/problems/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

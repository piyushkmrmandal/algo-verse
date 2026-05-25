package com.algoverse.problem.service;

import com.algoverse.problem.application.dto.ProblemDetailDto;
import com.algoverse.problem.application.dto.ProblemPageDto;
import com.algoverse.problem.application.usecase.CreateProblemUseCase;
import com.algoverse.problem.application.usecase.GetProblemUseCase;
import com.algoverse.problem.application.usecase.GetProblemsUseCase;
import com.algoverse.problem.application.dto.CreateProblemRequest;
import com.algoverse.problem.domain.exception.NotFoundException;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.model.TestCase;
import com.algoverse.problem.domain.repository.ProblemRepository;
import com.algoverse.problem.domain.repository.ProblemTopicRepository;
import com.algoverse.problem.domain.repository.TestCaseRepository;
import com.algoverse.problem.domain.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the core problem-service use-case layer.
 *
 * Covers the five scenarios from the spec:
 *   1. getProblems — no filters → paginated list
 *   2. getProblems — with difficulty filter → filtered results
 *   3. getProblemBySlug — existing slug → ProblemDetail
 *   4. getProblemBySlug — unknown slug → 404 NotFoundException
 *   5. createProblem — valid request → saves to all stores
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemService use-case unit tests")
class ProblemServiceTest {

    // ── GetProblems ──────────────────────────────────────────────────────────

    @Mock
    private ProblemRepository problemRepository;

    @InjectMocks
    private GetProblemsUseCase getProblemsUseCase;

    // ── GetProblem ───────────────────────────────────────────────────────────

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ProblemTopicRepository problemTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    private GetProblemUseCase getProblemUseCase;

    // ── CreateProblem ────────────────────────────────────────────────────────

    private CreateProblemUseCase createProblemUseCase;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private UUID problemId;
    private Problem twoSum;

    @BeforeEach
    void setUp() {
        problemId = UUID.randomUUID();
        twoSum = Problem.builder()
                .id(problemId)
                .slug("two-sum")
                .title("Two Sum")
                .description("Given an integer array and a target, return indices of two numbers that sum to target.")
                .difficulty(Difficulty.EASY)
                .tagsRaw("array,hash-table")
                .isPremium(false)
                .isPublished(true)
                .totalSubmissions(10_000)
                .acceptedSubmissions(6_000)
                .timeLimit(1000)
                .memoryLimit(256)
                .build();

        // Manually wire use cases that need multiple mocks
        getProblemUseCase = new GetProblemUseCase(
                problemRepository, testCaseRepository, problemTopicRepository, topicRepository);
        createProblemUseCase = new CreateProblemUseCase(
                problemRepository, testCaseRepository, problemTopicRepository, topicRepository);
    }

    // ========================================================================
    // 1. getProblems_noFilters_returnsPaginatedList
    // ========================================================================

    @Test
    @DisplayName("getProblems: no filters → paginated list with correct totals")
    void getProblems_noFilters_returnsPaginatedList() {
        when(problemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(twoSum)));

        ProblemPageDto result = getProblemsUseCase.execute(0, 20, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).slug()).isEqualTo("two-sum");
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
    }

    // ========================================================================
    // 2. getProblems_withDifficultyFilter_returnsFiltered
    // ========================================================================

    @Test
    @DisplayName("getProblems: with difficulty=EASY filter → only easy problems returned")
    void getProblems_withDifficultyFilter_returnsFiltered() {
        when(problemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(twoSum)));

        ProblemPageDto result = getProblemsUseCase.execute(0, 20, "EASY", null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).difficulty()).isEqualTo(Difficulty.EASY);
    }

    @Test
    @DisplayName("getProblems: with difficulty=HARD filter → empty page when no matches")
    void getProblems_withDifficultyFilter_returnsEmpty() {
        when(problemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ProblemPageDto result = getProblemsUseCase.execute(0, 20, "HARD", null, null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
    }

    // ========================================================================
    // 3. getProblemBySlug_existing_returnsProblemDetail
    // ========================================================================

    @Test
    @DisplayName("getProblemBySlug: existing slug → full ProblemDetail with test cases")
    void getProblemBySlug_existing_returnsProblemDetail() {
        TestCase sample = TestCase.builder()
                .id(UUID.randomUUID())
                .problemId(problemId)
                .input("[2,7,11,15]\n9")
                .expectedOutput("[0,1]")
                .isSample(true)
                .explanation("nums[0] + nums[1] == 9")
                .orderIndex(1)
                .build();

        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(twoSum));
        when(testCaseRepository.findByProblemIdAndIsSampleTrue(problemId)).thenReturn(List.of(sample));
        when(problemTopicRepository.findByProblemId(problemId)).thenReturn(List.of());

        ProblemDetailDto detail = getProblemUseCase.execute("two-sum");

        assertThat(detail).isNotNull();
        assertThat(detail.slug()).isEqualTo("two-sum");
        assertThat(detail.title()).isEqualTo("Two Sum");
        assertThat(detail.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(detail.sampleTestCases()).hasSize(1);
        assertThat(detail.sampleTestCases().get(0).input()).isEqualTo("[2,7,11,15]\n9");
        assertThat(detail.acceptanceRate()).isEqualTo(60.0);  // 6000/10000 * 100
    }

    // ========================================================================
    // 4. getProblemBySlug_notFound_throws404
    // ========================================================================

    @Test
    @DisplayName("getProblemBySlug: unknown slug → NotFoundException (404)")
    void getProblemBySlug_notFound_throws404() {
        when(problemRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getProblemUseCase.execute("unknown-slug"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Problem")
                .hasMessageContaining("unknown-slug");
    }

    // ========================================================================
    // 5. createProblem_validRequest_savesToAllStores
    // ========================================================================

    @Test
    @DisplayName("createProblem: valid admin request → saved to repository and returned as ProblemDetail")
    void createProblem_validRequest_savesToAllStores() {
        CreateProblemRequest request = new CreateProblemRequest(
                "merge-intervals",
                "Merge Intervals",
                "Given an array of intervals, merge all overlapping intervals.",
                Difficulty.MEDIUM,
                1000,
                256,
                List.of("array", "sorting"),
                "1 <= intervals.length <= 10^4",
                "List of intervals",
                "Merged intervals list",
                false
        );

        Problem saved = Problem.builder()
                .id(UUID.randomUUID())
                .slug(request.slug())
                .title(request.title())
                .description(request.description())
                .difficulty(request.difficulty())
                .timeLimit(request.timeLimit())
                .memoryLimit(request.memoryLimit())
                .isPremium(false)
                .isPublished(true)
                .totalSubmissions(0)
                .acceptedSubmissions(0)
                .build();

        when(problemRepository.existsBySlug(request.slug())).thenReturn(false);
        when(problemRepository.existsByTitle(request.title())).thenReturn(false);
        when(problemRepository.save(any(Problem.class))).thenReturn(saved);

        ProblemDetailDto result = createProblemUseCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.slug()).isEqualTo("merge-intervals");
        assertThat(result.title()).isEqualTo("Merge Intervals");
        assertThat(result.difficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(result.acceptanceRate()).isEqualTo(0.0);

        verify(problemRepository, times(1)).save(any(Problem.class));
    }
}

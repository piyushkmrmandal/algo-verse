package com.algoverse.problem.application.usecase;

import com.algoverse.problem.application.dto.ProblemDetailDto;
import com.algoverse.problem.domain.exception.NotFoundException;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.model.TestCase;
import com.algoverse.problem.domain.repository.ProblemRepository;
import com.algoverse.problem.domain.repository.ProblemTopicRepository;
import com.algoverse.problem.domain.repository.TestCaseRepository;
import com.algoverse.problem.domain.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProblemUseCaseTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ProblemTopicRepository problemTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @InjectMocks
    private GetProblemUseCase useCase;

    private Problem sampleProblem;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        problemId = UUID.randomUUID();
        sampleProblem = Problem.builder()
                .id(problemId)
                .slug("two-sum")
                .title("Two Sum")
                .description("Given an array of integers, return indices of two numbers that add up to target.")
                .difficulty(Difficulty.EASY)
                .tagsRaw("array,hash-table")
                .isPremium(false)
                .isPublished(true)
                .totalSubmissions(1000)
                .acceptedSubmissions(750)
                .timeLimit(1000)
                .memoryLimit(256)
                .build();
    }

    @Test
    void execute_shouldReturnProblemDetail() {
        TestCase tc = TestCase.builder()
                .id(UUID.randomUUID())
                .problemId(problemId)
                .input("[2,7,11,15]\n9")
                .expectedOutput("[0,1]")
                .isSample(true)
                .explanation("nums[0] + nums[1] == 9")
                .orderIndex(1)
                .build();

        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(sampleProblem));
        when(testCaseRepository.findByProblemIdAndIsSampleTrue(problemId)).thenReturn(List.of(tc));
        when(problemTopicRepository.findByProblemId(problemId)).thenReturn(List.of());

        ProblemDetailDto result = useCase.execute("two-sum");

        assertThat(result).isNotNull();
        assertThat(result.slug()).isEqualTo("two-sum");
        assertThat(result.title()).isEqualTo("Two Sum");
        assertThat(result.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(result.sampleTestCases()).hasSize(1);
        assertThat(result.sampleTestCases().get(0).input()).isEqualTo("[2,7,11,15]\n9");
        assertThat(result.acceptanceRate()).isEqualTo(75.0);
    }

    @Test
    void execute_shouldThrowNotFoundExceptionForUnknownSlug() {
        when(problemRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("unknown-slug"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Problem")
                .hasMessageContaining("unknown-slug");
    }

    @Test
    void execute_shouldReturnEmptySampleTestCasesWhenNoneExist() {
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(sampleProblem));
        when(testCaseRepository.findByProblemIdAndIsSampleTrue(problemId)).thenReturn(List.of());
        when(problemTopicRepository.findByProblemId(problemId)).thenReturn(List.of());

        ProblemDetailDto result = useCase.execute("two-sum");

        assertThat(result.sampleTestCases()).isEmpty();
        assertThat(result.topics()).isEmpty();
    }
}

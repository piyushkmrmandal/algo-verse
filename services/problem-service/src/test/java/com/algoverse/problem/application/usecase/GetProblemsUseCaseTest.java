package com.algoverse.problem.application.usecase;

import com.algoverse.problem.application.dto.ProblemPageDto;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProblemsUseCaseTest {

    @Mock
    private ProblemRepository problemRepository;

    @InjectMocks
    private GetProblemsUseCase useCase;

    private Problem sampleProblem;

    @BeforeEach
    void setUp() {
        sampleProblem = Problem.builder()
                .id(UUID.randomUUID())
                .slug("two-sum")
                .title("Two Sum")
                .difficulty(Difficulty.EASY)
                .tagsRaw("array,hash-table")
                .isPremium(false)
                .isPublished(true)
                .totalSubmissions(1000)
                .acceptedSubmissions(750)
                .build();
    }

    @Test
    void execute_shouldReturnPagedProblems() {
        when(problemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleProblem)));

        ProblemPageDto result = useCase.execute(0, 20, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).slug()).isEqualTo("two-sum");
        assertThat(result.items().get(0).acceptanceRate()).isEqualTo(75.0);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void execute_shouldReturnEmptyPageWhenNoResults() {
        when(problemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ProblemPageDto result = useCase.execute(0, 20, "HARD", null, null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    void execute_shouldComputeAcceptanceRateCorrectly() {
        Problem zeroSubmissions = Problem.builder()
                .id(UUID.randomUUID())
                .slug("no-submissions")
                .title("No Submissions")
                .difficulty(Difficulty.MEDIUM)
                .isPublished(true)
                .totalSubmissions(0)
                .acceptedSubmissions(0)
                .build();

        when(problemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(zeroSubmissions)));

        ProblemPageDto result = useCase.execute(0, 20, null, null, null, null);

        assertThat(result.items().get(0).acceptanceRate()).isEqualTo(0.0);
    }
}

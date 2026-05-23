package com.algoverse.problem.application.usecase;

import com.algoverse.problem.application.dto.CreateProblemRequest;
import com.algoverse.problem.application.dto.ProblemDetailDto;
import com.algoverse.problem.domain.exception.ConflictException;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.repository.ProblemRepository;
import com.algoverse.problem.domain.repository.ProblemTopicRepository;
import com.algoverse.problem.domain.repository.TestCaseRepository;
import com.algoverse.problem.domain.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProblemUseCaseTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ProblemTopicRepository problemTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @InjectMocks
    private CreateProblemUseCase useCase;

    @Test
    void execute_shouldCreateProblemSuccessfully() {
        CreateProblemRequest request = new CreateProblemRequest(
                "merge-sorted-lists",
                "Merge Two Sorted Lists",
                "Merge two sorted linked lists.",
                Difficulty.EASY,
                1000,
                256,
                List.of("linked-list"),
                "1 <= n <= 50",
                "Two sorted linked lists",
                "One merged sorted linked list",
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
                .isPublished(true)
                .totalSubmissions(0)
                .acceptedSubmissions(0)
                .build();

        when(problemRepository.existsBySlug(request.slug())).thenReturn(false);
        when(problemRepository.existsByTitle(request.title())).thenReturn(false);
        when(problemRepository.save(any(Problem.class))).thenReturn(saved);

        ProblemDetailDto result = useCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.slug()).isEqualTo("merge-sorted-lists");
        assertThat(result.title()).isEqualTo("Merge Two Sorted Lists");
        assertThat(result.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(result.acceptanceRate()).isEqualTo(0.0);
    }

    @Test
    void execute_shouldThrowConflictWhenSlugAlreadyExists() {
        CreateProblemRequest request = new CreateProblemRequest(
                "two-sum", "Another Two Sum", "Description",
                Difficulty.EASY, 1000, 256, null, null, null, null, false
        );

        when(problemRepository.existsBySlug("two-sum")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("two-sum");
    }

    @Test
    void execute_shouldThrowConflictWhenTitleAlreadyExists() {
        CreateProblemRequest request = new CreateProblemRequest(
                "unique-slug", "Two Sum", "Description",
                Difficulty.EASY, 1000, 256, null, null, null, null, false
        );

        when(problemRepository.existsBySlug("unique-slug")).thenReturn(false);
        when(problemRepository.existsByTitle("Two Sum")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Two Sum");
    }
}

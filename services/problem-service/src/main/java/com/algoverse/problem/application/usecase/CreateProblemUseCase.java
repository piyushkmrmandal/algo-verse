package com.algoverse.problem.application.usecase;

import com.algoverse.problem.application.dto.CreateProblemRequest;
import com.algoverse.problem.application.dto.ProblemDetailDto;
import com.algoverse.problem.application.dto.TestCaseDto;
import com.algoverse.problem.domain.exception.ConflictException;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.repository.ProblemRepository;
import com.algoverse.problem.domain.repository.ProblemTopicRepository;
import com.algoverse.problem.domain.repository.TestCaseRepository;
import com.algoverse.problem.domain.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateProblemUseCase {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProblemTopicRepository problemTopicRepository;
    private final TopicRepository topicRepository;

    @Transactional
    @CacheEvict(value = "problems", allEntries = true)
    public ProblemDetailDto execute(CreateProblemRequest request) {
        log.info("Creating new problem with slug: {}", request.slug());

        if (problemRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Problem", "slug", request.slug());
        }

        if (problemRepository.existsByTitle(request.title())) {
            throw new ConflictException("Problem", "title", request.title());
        }

        Problem problem = Problem.builder()
                .slug(request.slug())
                .title(request.title())
                .description(request.description())
                .difficulty(request.difficulty())
                .constraints(request.constraints())
                .inputFormat(request.inputFormat())
                .outputFormat(request.outputFormat())
                .timeLimit(request.timeLimit())
                .memoryLimit(request.memoryLimit())
                .isPremium(request.isPremium())
                .isPublished(true)
                .totalSubmissions(0)
                .acceptedSubmissions(0)
                .build();

        if (request.tags() != null) {
            problem.setTags(request.tags());
        }

        Problem saved = problemRepository.save(problem);
        log.info("Problem created with id: {}", saved.getId());

        double rate = 0.0;
        return new ProblemDetailDto(
                saved.getId(),
                saved.getSlug(),
                saved.getTitle(),
                saved.getDifficulty(),
                saved.getTags(),
                saved.isPremium(),
                saved.getTotalSubmissions(),
                saved.getAcceptedSubmissions(),
                rate,
                saved.getDescription(),
                saved.getConstraints(),
                saved.getInputFormat(),
                saved.getOutputFormat(),
                saved.getTimeLimit(),
                saved.getMemoryLimit(),
                List.of(),
                List.of()
        );
    }
}

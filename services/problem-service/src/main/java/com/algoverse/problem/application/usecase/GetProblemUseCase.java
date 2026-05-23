package com.algoverse.problem.application.usecase;

import com.algoverse.problem.application.dto.ProblemDetailDto;
import com.algoverse.problem.application.dto.TestCaseDto;
import com.algoverse.problem.domain.exception.NotFoundException;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.model.ProblemTopic;
import com.algoverse.problem.domain.model.TestCase;
import com.algoverse.problem.domain.model.Topic;
import com.algoverse.problem.domain.repository.ProblemRepository;
import com.algoverse.problem.domain.repository.ProblemTopicRepository;
import com.algoverse.problem.domain.repository.TestCaseRepository;
import com.algoverse.problem.domain.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetProblemUseCase {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProblemTopicRepository problemTopicRepository;
    private final TopicRepository topicRepository;

    @Cacheable(value = "problems", key = "#slug")
    public ProblemDetailDto execute(String slug) {
        log.debug("Fetching problem detail for slug: {}", slug);

        Problem problem = problemRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Problem", "slug", slug));

        List<TestCase> sampleCases = testCaseRepository.findByProblemIdAndIsSampleTrue(problem.getId());
        List<String> topicNames = resolveTopicNames(problem.getId());

        return toDetailDto(problem, sampleCases, topicNames);
    }

    private List<String> resolveTopicNames(UUID problemId) {
        List<ProblemTopic> problemTopics = problemTopicRepository.findByProblemId(problemId);
        return problemTopics.stream()
                .map(pt -> topicRepository.findById(pt.getTopicId()))
                .filter(java.util.Optional::isPresent)
                .map(opt -> opt.get().getName())
                .toList();
    }

    private ProblemDetailDto toDetailDto(Problem problem, List<TestCase> sampleCases, List<String> topicNames) {
        double rate = problem.getAcceptedSubmissions() / (double) Math.max(1, problem.getTotalSubmissions()) * 100.0;
        double acceptanceRate = Math.round(rate * 100.0) / 100.0;

        List<TestCaseDto> sampleDtos = sampleCases.stream()
                .map(tc -> new TestCaseDto(tc.getId(), tc.getInput(), tc.getExpectedOutput(), tc.getExplanation()))
                .toList();

        return new ProblemDetailDto(
                problem.getId(),
                problem.getSlug(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getTags(),
                problem.isPremium(),
                problem.getTotalSubmissions(),
                problem.getAcceptedSubmissions(),
                acceptanceRate,
                problem.getDescription(),
                problem.getConstraints(),
                problem.getInputFormat(),
                problem.getOutputFormat(),
                problem.getTimeLimit(),
                problem.getMemoryLimit(),
                sampleDtos,
                topicNames
        );
    }
}

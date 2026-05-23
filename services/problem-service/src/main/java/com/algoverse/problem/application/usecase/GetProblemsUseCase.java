package com.algoverse.problem.application.usecase;

import com.algoverse.problem.application.dto.ProblemPageDto;
import com.algoverse.problem.application.dto.ProblemSummaryDto;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.repository.ProblemRepository;
import com.algoverse.problem.infrastructure.spec.ProblemSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetProblemsUseCase {

    private final ProblemRepository problemRepository;

    public ProblemPageDto execute(int page, int size, String difficulty, String topic, String search, Boolean premium) {
        log.debug("Fetching problems: page={}, size={}, difficulty={}, topic={}, search={}, premium={}",
                page, size, difficulty, topic, search, premium);

        List<Specification<Problem>> specs = new ArrayList<>();

        // Parse difficulty
        if (StringUtils.hasText(difficulty)) {
            try {
                Difficulty diffEnum = Difficulty.valueOf(difficulty.toUpperCase());
                specs.add(ProblemSpecification.withDifficulty(diffEnum));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid difficulty filter value: {}", difficulty);
            }
        }

        // Topic filter
        if (StringUtils.hasText(topic)) {
            specs.add(ProblemSpecification.withTopicSlug(topic));
        }

        // Search filter
        if (StringUtils.hasText(search)) {
            specs.add(ProblemSpecification.withSearch(search));
        }

        // Premium filter
        if (premium != null) {
            specs.add(ProblemSpecification.isPremium(premium));
        }

        // Always filter by published = true for public listing
        specs.add(ProblemSpecification.isPublished(true));

        Specification<Problem> combinedSpec = ProblemSpecification.compose(specs);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Problem> resultPage = problemRepository.findAll(combinedSpec, pageRequest);

        List<ProblemSummaryDto> items = resultPage.getContent().stream()
                .map(this::toSummaryDto)
                .toList();

        return new ProblemPageDto(
                items,
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.hasNext()
        );
    }

    private ProblemSummaryDto toSummaryDto(Problem problem) {
        return ProblemSummaryDto.from(
                problem.getId(),
                problem.getSlug(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getTags(),
                problem.isPremium(),
                problem.getTotalSubmissions(),
                problem.getAcceptedSubmissions()
        );
    }
}

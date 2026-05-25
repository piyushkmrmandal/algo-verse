package com.algoverse.sysdesign.service;

import com.algoverse.sysdesign.domain.SysdesignProblem;
import com.algoverse.sysdesign.dto.ProblemDetail;
import com.algoverse.sysdesign.dto.ProblemSummary;
import com.algoverse.sysdesign.repository.SysdesignProblemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final String PROBLEMS_CACHE_PREFIX = "problems:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final SysdesignProblemRepository problemRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @SuppressWarnings("unchecked")
    public Page<ProblemSummary> getProblems(String category, Pageable pageable) {
        String cacheKey = PROBLEMS_CACHE_PREFIX + (category != null ? category : "all")
            + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for problems key: {}", cacheKey);
            return (Page<ProblemSummary>) cached;
        }

        Page<SysdesignProblem> problems = (category != null && !category.isBlank())
            ? problemRepository.findByCategoryAndPublishedTrue(category, pageable)
            : problemRepository.findByPublishedTrue(pageable);

        Page<ProblemSummary> summaries = problems.map(this::toSummary);

        redisTemplate.opsForValue().set(cacheKey, summaries, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return summaries;
    }

    public ProblemDetail getProblemBySlug(String slug) {
        return problemRepository.findBySlug(slug)
            .map(this::toDetail)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Problem not found: " + slug));
    }

    public SysdesignProblem getProblemEntityBySlug(String slug) {
        return problemRepository.findBySlug(slug)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Problem not found: " + slug));
    }

    private ProblemSummary toSummary(SysdesignProblem p) {
        return new ProblemSummary(p.getId(), p.getSlug(), p.getTitle(),
            p.getDifficulty(), p.getCategory(), p.isPublished());
    }

    private ProblemDetail toDetail(SysdesignProblem p) {
        return new ProblemDetail(p.getId(), p.getSlug(), p.getTitle(),
            p.getDifficulty(), p.getCategory(), p.getDescriptionMd(),
            p.getRequirements(), p.isPublished(), p.getCreatedAt());
    }
}

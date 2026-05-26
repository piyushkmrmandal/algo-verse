package com.algoverse.sysdesign.service;

import com.algoverse.sysdesign.domain.SysdesignProblem;
import com.algoverse.sysdesign.dto.ProblemDetail;
import com.algoverse.sysdesign.dto.ProblemSummary;
import com.algoverse.sysdesign.repository.SysdesignProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemService unit tests")
class ProblemServiceTest {

    @Mock private SysdesignProblemRepository problemRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks private ProblemService problemService;

    private static final UUID PROB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private SysdesignProblem buildProblem(String slug, String category, String difficulty) {
        return SysdesignProblem.builder()
                .id(PROB_ID)
                .slug(slug)
                .title("Design " + slug)
                .difficulty(difficulty)
                .category(category)
                .descriptionMd("## Design " + slug)
                .requirements(List.of("Handle 1M RPS", "Low latency"))
                .published(true)
                .createdAt(Instant.now())
                .build();
    }

    // ── Redis caching ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Redis caching")
    class RedisCaching {

        @Test
        @DisplayName("cache hit returns cached result without hitting DB")
        void getProblems_cacheHit_doesNotQueryDb() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<ProblemSummary> cached = new PageImpl<>(List.of(
                    new ProblemSummary(PROB_ID, "design-twitter", "Design Twitter", "HARD", "Social", true)
            ));

            given(valueOps.get(anyString())).willReturn(cached);

            Page<ProblemSummary> result = problemService.getProblems(null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).slug()).isEqualTo("design-twitter");
            then(problemRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("cache miss queries DB and stores result in Redis")
        void getProblems_cacheMiss_queriesDbAndCaches() {
            Pageable pageable = PageRequest.of(0, 20);
            SysdesignProblem problem = buildProblem("design-url-shortener", "Storage", "MEDIUM");
            Page<SysdesignProblem> dbPage = new PageImpl<>(List.of(problem));

            given(valueOps.get(anyString())).willReturn(null);
            given(problemRepository.findByPublishedTrue(pageable)).willReturn(dbPage);

            Page<ProblemSummary> result = problemService.getProblems(null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).slug()).isEqualTo("design-url-shortener");
            then(valueOps).should().set(anyString(), any(), eq(5L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("cache key includes category when provided")
        void getProblems_withCategory_usesCategoryInCacheKey() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<SysdesignProblem> dbPage = new PageImpl<>(List.of(
                    buildProblem("design-s3", "Storage", "HARD")
            ));

            given(valueOps.get(anyString())).willReturn(null);
            given(problemRepository.findByCategoryAndPublishedTrue("Storage", pageable))
                    .willReturn(dbPage);

            problemService.getProblems("Storage", pageable);

            // Verify DB query used category-specific repo method
            then(problemRepository).should().findByCategoryAndPublishedTrue("Storage", pageable);
            then(problemRepository).should(never()).findByPublishedTrue(any());
        }

        @Test
        @DisplayName("null category queries all published problems")
        void getProblems_noCategory_queriesAllPublished() {
            Pageable pageable = PageRequest.of(0, 10);
            given(valueOps.get(anyString())).willReturn(null);
            given(problemRepository.findByPublishedTrue(pageable))
                    .willReturn(Page.empty());

            problemService.getProblems(null, pageable);

            then(problemRepository).should().findByPublishedTrue(pageable);
            then(problemRepository).should(never()).findByCategoryAndPublishedTrue(any(), any());
        }
    }

    // ── getProblemBySlug ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProblemBySlug")
    class GetProblemBySlug {

        @Test
        @DisplayName("found slug returns full ProblemDetail")
        void getProblemBySlug_found_returnsDetail() {
            SysdesignProblem problem = buildProblem("design-twitter", "Social", "HARD");
            given(problemRepository.findBySlug("design-twitter"))
                    .willReturn(Optional.of(problem));

            ProblemDetail detail = problemService.getProblemBySlug("design-twitter");

            assertThat(detail.slug()).isEqualTo("design-twitter");
            assertThat(detail.title()).isEqualTo("Design design-twitter");
            assertThat(detail.difficulty()).isEqualTo("HARD");
            assertThat(detail.category()).isEqualTo("Social");
            assertThat(detail.descriptionMd()).startsWith("##");
            assertThat(detail.requirements()).containsExactly("Handle 1M RPS", "Low latency");
        }

        @Test
        @DisplayName("unknown slug throws 404")
        void getProblemBySlug_notFound_throws404() {
            given(problemRepository.findBySlug("does-not-exist"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.getProblemBySlug("does-not-exist"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rse.getMessage()).contains("does-not-exist");
                    });
        }
    }

    // ── getProblemEntityBySlug ────────────────────────────────────────────────

    @Nested
    @DisplayName("getProblemEntityBySlug")
    class GetProblemEntityBySlug {

        @Test
        @DisplayName("returns entity for known slug")
        void getProblemEntityBySlug_found_returnsEntity() {
            SysdesignProblem problem = buildProblem("design-kafka", "Messaging", "HARD");
            given(problemRepository.findBySlug("design-kafka"))
                    .willReturn(Optional.of(problem));

            SysdesignProblem result = problemService.getProblemEntityBySlug("design-kafka");

            assertThat(result.getSlug()).isEqualTo("design-kafka");
            assertThat(result.getCategory()).isEqualTo("Messaging");
        }

        @Test
        @DisplayName("throws 404 for unknown slug")
        void getProblemEntityBySlug_notFound_throws404() {
            given(problemRepository.findBySlug("ghost")).willReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.getProblemEntityBySlug("ghost"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode())
                                    .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── Summary mapping ───────────────────────────────────────────────────────

    @Test
    @DisplayName("problem summary maps all fields correctly from domain object")
    void getProblems_summaryMappedCorrectly() {
        Pageable pageable = PageRequest.of(0, 20);
        SysdesignProblem problem = buildProblem("design-redis", "Storage", "MEDIUM");
        given(valueOps.get(anyString())).willReturn(null);
        given(problemRepository.findByPublishedTrue(pageable))
                .willReturn(new PageImpl<>(List.of(problem)));

        Page<ProblemSummary> result = problemService.getProblems(null, pageable);

        ProblemSummary summary = result.getContent().get(0);
        assertThat(summary.id()).isEqualTo(PROB_ID);
        assertThat(summary.slug()).isEqualTo("design-redis");
        assertThat(summary.difficulty()).isEqualTo("MEDIUM");
        assertThat(summary.category()).isEqualTo("Storage");
        assertThat(summary.published()).isTrue();
    }
}

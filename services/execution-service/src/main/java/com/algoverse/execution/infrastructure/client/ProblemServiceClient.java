package com.algoverse.execution.infrastructure.client;

import com.algoverse.execution.application.dto.ProblemMetaDto;
import com.algoverse.execution.application.dto.TestCaseDto;
import com.algoverse.execution.domain.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * HTTP client for the problem-service.
 *
 * <p>Uses Spring 6.1 {@link RestClient} (fluent, synchronous) to call the
 * problem-service REST API. Throws {@link NotFoundException} on 404 and
 * propagates {@link org.springframework.web.client.RestClientException} for
 * other HTTP errors (caught by the global exception handler → 500).
 */
@Slf4j
@Service
public class ProblemServiceClient {

    private final RestClient restClient;

    public ProblemServiceClient(
            @Value("${problem-service.base-url:http://problem-service:8082}") String baseUrl,
            RestClient.Builder restClientBuilder) {

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        throw new NotFoundException("Resource not found on problem-service: " + req.getURI());
                    }
                    throw new RuntimeException("4xx error from problem-service: " + res.getStatusCode());
                })
                .build();
    }

    /**
     * Fetches all test cases for a problem, including hidden ones used for judging.
     *
     * @param problemId UUID of the problem in problem-service.
     * @return Ordered list of test case DTOs.
     */
    public List<TestCaseDto> getTestCases(UUID problemId) {
        log.debug("Fetching test cases for problemId={}", problemId);
        return restClient.get()
                .uri("/api/v1/problems/{id}/test-cases", problemId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TestCaseDto>>() {});
    }

    /**
     * Fetches lightweight problem metadata (time/memory limits, slug, difficulty).
     *
     * @param problemId UUID of the problem in problem-service.
     * @return Problem metadata DTO.
     */
    public ProblemMetaDto getProblemMeta(UUID problemId) {
        log.debug("Fetching problem meta for problemId={}", problemId);
        return restClient.get()
                .uri("/api/v1/problems/{id}/meta", problemId)
                .retrieve()
                .body(ProblemMetaDto.class);
    }
}

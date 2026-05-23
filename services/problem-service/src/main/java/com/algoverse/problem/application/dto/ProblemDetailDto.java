package com.algoverse.problem.application.dto;

import com.algoverse.problem.domain.model.Difficulty;

import java.util.List;
import java.util.UUID;

public record ProblemDetailDto(
        UUID id,
        String slug,
        String title,
        Difficulty difficulty,
        List<String> tags,
        boolean isPremium,
        int totalSubmissions,
        int acceptedSubmissions,
        double acceptanceRate,
        String description,
        String constraints,
        String inputFormat,
        String outputFormat,
        Integer timeLimit,
        Integer memoryLimit,
        List<TestCaseDto> sampleTestCases,
        List<String> topics
) {
}

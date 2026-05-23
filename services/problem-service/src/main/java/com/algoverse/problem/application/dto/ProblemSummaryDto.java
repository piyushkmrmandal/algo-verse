package com.algoverse.problem.application.dto;

import com.algoverse.problem.domain.model.Difficulty;

import java.util.List;
import java.util.UUID;

public record ProblemSummaryDto(
        UUID id,
        String slug,
        String title,
        Difficulty difficulty,
        List<String> tags,
        boolean isPremium,
        int totalSubmissions,
        int acceptedSubmissions,
        double acceptanceRate
) {
    public static ProblemSummaryDto from(
            UUID id,
            String slug,
            String title,
            Difficulty difficulty,
            List<String> tags,
            boolean isPremium,
            int totalSubmissions,
            int acceptedSubmissions
    ) {
        double rate = acceptedSubmissions / (double) Math.max(1, totalSubmissions) * 100.0;
        return new ProblemSummaryDto(
                id, slug, title, difficulty, tags, isPremium,
                totalSubmissions, acceptedSubmissions,
                Math.round(rate * 100.0) / 100.0
        );
    }
}

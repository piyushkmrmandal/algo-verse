package com.algoverse.problem.application.dto;

import java.util.List;

public record ProblemPageDto(
        List<ProblemSummaryDto> items,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean hasNext
) {
}

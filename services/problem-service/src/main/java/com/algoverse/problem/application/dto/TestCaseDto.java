package com.algoverse.problem.application.dto;

import java.util.UUID;

public record TestCaseDto(
        UUID id,
        String input,
        String expectedOutput,
        String explanation
) {
}

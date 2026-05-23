package com.algoverse.execution.application.dto;

import java.util.UUID;

/**
 * Lightweight projection of a test case fetched from problem-service.
 *
 * @param id             Unique identifier of the test case.
 * @param input          Stdin payload for the sandbox.
 * @param expectedOutput Expected stdout (compared after trimming whitespace).
 * @param isSample       True when this test case is publicly visible to users.
 */
public record TestCaseDto(
        UUID id,
        String input,
        String expectedOutput,
        boolean isSample
) {}

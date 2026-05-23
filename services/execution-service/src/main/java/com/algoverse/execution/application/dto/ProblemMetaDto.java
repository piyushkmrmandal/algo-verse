package com.algoverse.execution.application.dto;

import java.util.UUID;

/**
 * Metadata about a problem fetched from problem-service.
 *
 * @param id          Problem UUID.
 * @param slug        Human-readable URL slug (e.g. {@code "two-sum"}).
 * @param timeLimit   Per-test-case time limit in milliseconds.
 * @param memoryLimit Per-test-case memory limit in megabytes.
 * @param difficulty  Raw difficulty string ("EASY" | "MEDIUM" | "HARD").
 */
public record ProblemMetaDto(
        UUID id,
        String slug,
        int timeLimit,
        int memoryLimit,
        String difficulty
) {}

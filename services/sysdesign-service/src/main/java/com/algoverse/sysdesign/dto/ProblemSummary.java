package com.algoverse.sysdesign.dto;

import java.util.UUID;

public record ProblemSummary(
    UUID id,
    String slug,
    String title,
    String difficulty,
    String category,
    boolean published
) {}

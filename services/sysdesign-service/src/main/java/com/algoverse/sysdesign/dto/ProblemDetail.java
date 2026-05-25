package com.algoverse.sysdesign.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemDetail(
    UUID id,
    String slug,
    String title,
    String difficulty,
    String category,
    String descriptionMd,
    List<String> requirements,
    boolean published,
    Instant createdAt
) {}

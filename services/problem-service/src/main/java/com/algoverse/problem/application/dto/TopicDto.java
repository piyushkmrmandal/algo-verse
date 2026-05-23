package com.algoverse.problem.application.dto;

import java.util.UUID;

public record TopicDto(
        UUID id,
        String name,
        String slug,
        String description
) {
}

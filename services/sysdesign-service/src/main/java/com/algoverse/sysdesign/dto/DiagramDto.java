package com.algoverse.sysdesign.dto;

import com.algoverse.sysdesign.domain.DiagramContent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DiagramDto(
    UUID id,
    UUID userId,
    UUID problemId,
    String title,
    int version,
    boolean submitted,
    Instant createdAt,
    Instant updatedAt,
    // content from MongoDB (nullable when listing without full content)
    List<DiagramContent.DiagramNode> nodes,
    List<DiagramContent.DiagramEdge> edges,
    Map<String, Object> metadata
) {}

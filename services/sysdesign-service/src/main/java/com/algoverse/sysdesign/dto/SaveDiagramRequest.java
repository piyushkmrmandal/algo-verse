package com.algoverse.sysdesign.dto;

import com.algoverse.sysdesign.domain.DiagramContent;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SaveDiagramRequest(
    @NotNull UUID problemId,
    String title,
    List<DiagramContent.DiagramNode> nodes,
    List<DiagramContent.DiagramEdge> edges,
    Map<String, Object> metadata
) {}

package com.algoverse.sysdesign.dto;

import java.util.List;

public record ReviewDiagramRequest(
    String diagramJson,
    String problemSlug,
    List<String> requirements
) {}

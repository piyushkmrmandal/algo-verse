package com.algoverse.visualization.domain;

import java.util.List;

public record TraceResponse(
        String language,
        List<TraceStep> steps,
        String finalOutput,
        String error,
        boolean truncated
) {}

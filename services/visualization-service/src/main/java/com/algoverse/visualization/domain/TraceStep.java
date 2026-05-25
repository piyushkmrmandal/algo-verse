package com.algoverse.visualization.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceStep(
        int step,
        String event,
        int line,
        String function,
        Map<String, String> locals,
        String stdout,
        String returnValue,
        String exception
) {}

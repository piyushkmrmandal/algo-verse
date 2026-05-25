package com.algoverse.visualization.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TraceRequest(
        @NotBlank
        @Pattern(regexp = "python|javascript", message = "language must be 'python' or 'javascript'")
        String language,

        @NotBlank
        @Size(max = 32_768, message = "code must be ≤ 32 KB")
        String code,

        @Size(max = 4_096)
        String input
) {
    public TraceRequest {
        if (input == null) input = "";
    }
}

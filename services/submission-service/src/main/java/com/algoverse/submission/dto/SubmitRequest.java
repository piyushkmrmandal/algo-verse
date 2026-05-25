package com.algoverse.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/v1/submissions.
 */
public record SubmitRequest(

        @NotBlank(message = "problemSlug must not be blank")
        String problemSlug,

        @NotBlank(message = "language must not be blank")
        @Pattern(
                regexp = "java|python|javascript|cpp|go|rust|kotlin",
                message = "language must be one of: java, python, javascript, cpp, go, rust, kotlin"
        )
        String language,

        @NotBlank(message = "code must not be blank")
        String code,

        /** Optional difficulty hint forwarded to graded Kafka event. Defaults to MEDIUM. */
        String difficulty
) {
    public SubmitRequest {
        if (difficulty == null || difficulty.isBlank()) {
            difficulty = "MEDIUM";
        }
    }
}

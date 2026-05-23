package com.algoverse.execution.application.dto;

import com.algoverse.execution.domain.model.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for the {@code POST /api/v1/submissions/run} endpoint.
 *
 * <p>Runs code against a user-supplied custom input without persisting a
 * submission or consuming the judge quota. Uses a lighter rate limit.
 *
 * @param language    Programming language.
 * @param code        Source code — max 65,536 characters.
 * @param customInput Stdin payload supplied by the user.
 */
public record RunRequest(

        @NotNull(message = "language is required")
        Language language,

        @NotBlank(message = "code must not be blank")
        @Size(max = 65536, message = "code must not exceed 65,536 characters")
        String code,

        @NotNull(message = "customInput is required")
        String customInput
) {}

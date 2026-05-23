package com.algoverse.execution.application.dto;

import com.algoverse.execution.domain.model.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for the {@code POST /api/v1/submissions} endpoint.
 *
 * @param problemId Target problem UUID — must exist in problem-service.
 * @param language  Programming language of the submitted code.
 * @param code      Source code — max 65,536 characters.
 */
public record SubmitRequest(

        @NotNull(message = "problemId is required")
        UUID problemId,

        @NotNull(message = "language is required")
        Language language,

        @NotBlank(message = "code must not be blank")
        @Size(max = 65536, message = "code must not exceed 65,536 characters")
        String code
) {}

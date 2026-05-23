package com.algoverse.problem.application.dto;

import com.algoverse.problem.domain.model.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProblemRequest(

        @NotBlank(message = "Slug must not be blank")
        @Size(max = 200, message = "Slug must not exceed 200 characters")
        String slug,

        @NotBlank(message = "Title must not be blank")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotBlank(message = "Description must not be blank")
        String description,

        @NotNull(message = "Difficulty must not be null")
        Difficulty difficulty,

        @NotNull(message = "Time limit must not be null")
        @Positive(message = "Time limit must be positive")
        Integer timeLimit,

        @NotNull(message = "Memory limit must not be null")
        @Positive(message = "Memory limit must be positive")
        Integer memoryLimit,

        List<String> tags,

        String constraints,

        String inputFormat,

        String outputFormat,

        boolean isPremium
) {
}

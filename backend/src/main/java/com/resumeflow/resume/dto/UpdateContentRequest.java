package com.resumeflow.resume.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateContentRequest(
    @NotNull @PositiveOrZero Long expectedVersion, @NotNull JsonNode content) {
}

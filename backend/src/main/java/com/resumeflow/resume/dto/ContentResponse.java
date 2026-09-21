package com.resumeflow.resume.dto;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ContentResponse(UUID resumeId, int versionNumber, JsonNode content, Instant updatedAt) {
}

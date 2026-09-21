package com.resumeflow.resume.dto;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record EditorStateResponse(
    ResumeResponse resume,
    JsonNode content,
    JsonNode template,
    List<SectionDto> sections,
    int latestVersion,
    UUID latestContentId) {
}

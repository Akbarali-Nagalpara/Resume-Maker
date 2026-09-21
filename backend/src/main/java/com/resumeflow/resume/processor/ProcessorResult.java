package com.resumeflow.resume.processor;

import tools.jackson.databind.JsonNode;

/**
 * Result of remote document processing. Content and template are carried as
 * JSON matching the {@code ResumeContentModel} / {@code TemplateMetadata}
 * shapes so no remapping is needed.
 */
public record ProcessorResult(
    String documentType,
    JsonNode content,
    JsonNode template,
    JsonNode sections,
    String sourceChecksum) {
}

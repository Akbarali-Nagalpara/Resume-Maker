package com.resumeflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for the Python document-processing service. */
@ConfigurationProperties(prefix = "document-processor")
public record DocumentProcessorProperties(
    String baseUrl, boolean enabled, int timeoutSeconds) {
}

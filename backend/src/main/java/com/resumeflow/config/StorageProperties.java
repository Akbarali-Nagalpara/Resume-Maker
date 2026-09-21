package com.resumeflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * File storage settings. Credentials are never stored here; the storage
 * location comes from the {@code RESUMEFLOW_STORAGE_PATH} environment variable.
 */
@ConfigurationProperties(prefix = "resumeflow.storage")
public record StorageProperties(String path) {
}

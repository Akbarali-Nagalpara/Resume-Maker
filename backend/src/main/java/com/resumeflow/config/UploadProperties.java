package com.resumeflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upload limits for resume documents.
 */
@ConfigurationProperties(prefix = "resumeflow.upload")
public record UploadProperties(long maxSizeMb) {

  public long maxSizeBytes() {
    return maxSizeMb * 1024L * 1024L;
  }
}

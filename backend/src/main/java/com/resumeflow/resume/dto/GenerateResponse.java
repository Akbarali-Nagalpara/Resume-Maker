package com.resumeflow.resume.dto;

import java.util.List;
import java.util.UUID;

public record GenerateResponse(
    UUID resumeId,
    int versionNumber,
    String storageKey,
    String checksum,
    String mimeType,
    List<String> warnings) {

  public GenerateResponse(
      UUID resumeId, int versionNumber, String storageKey, String checksum, String mimeType) {
    this(resumeId, versionNumber, storageKey, checksum, mimeType, List.of());
  }
}

package com.resumeflow.resume.dto;

import java.time.Instant;
import java.util.UUID;

public record VersionResponse(
    int versionNumber,
    UUID contentSnapshotId,
    UUID templateSnapshotId,
    String generatedStorageKey,
    Instant createdAt) {
}

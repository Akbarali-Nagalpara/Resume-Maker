package com.resumeflow.resume.dto;

import java.time.Instant;
import java.util.UUID;

/** REST representation of a resume. Never exposes the JPA entity. */
public record ResumeResponse(
    UUID id,
    String filename,
    String mimeType,
    String checksum,
    String status,
    long version,
    Instant createdAt,
    Instant updatedAt) {
}

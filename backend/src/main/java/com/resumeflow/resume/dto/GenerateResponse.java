package com.resumeflow.resume.dto;

import java.util.UUID;

public record GenerateResponse(
    UUID resumeId, int versionNumber, String storageKey, String checksum, String mimeType) {
}

package com.resumeflow.resume.dto;

import java.util.UUID;

public record PreviewResponse(UUID resumeId, int versionNumber, String storageKey, String mimeType) {
}

package com.resumeflow.resume.dto;

import java.util.UUID;

public record UploadResponse(UUID resumeId, String status, int version) {
}

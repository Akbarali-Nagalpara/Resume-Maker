package com.resumeflow.resume.generator;

/** Reference to a generated file on disk, before it is moved into storage. */
public record GeneratedArtifact(String mimeType, long sizeBytes, String sha256Hex) {
}

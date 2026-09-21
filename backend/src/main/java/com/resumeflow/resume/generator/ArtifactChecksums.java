package com.resumeflow.resume.generator;

import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.resume.storage.Sha256;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds {@link GeneratedArtifact} metadata for a finished output file. */
final class ArtifactChecksums {

  private ArtifactChecksums() {
  }

  static GeneratedArtifact of(Path outputFile, String mimeType) {
    try {
      long size = Files.size(outputFile);
      String checksum;
      try (InputStream in = Files.newInputStream(outputFile)) {
        checksum = Sha256.hexOf(in);
      }
      return new GeneratedArtifact(mimeType, size, checksum);
    } catch (Exception e) {
      throw new ResumeParseException("Cannot checksum generated artifact", e);
    }
  }
}

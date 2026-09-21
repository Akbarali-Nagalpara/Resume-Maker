package com.resumeflow.resume.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Converts a patched DOCX into PDF without redesigning it. Implementations
 * must use a real document renderer (e.g. LibreOffice headless), never a
 * generic re-render, so the original template survives the conversion.
 */
public interface DocxToPdfConverter {

  /** Whether a conversion engine is available in this environment. */
  boolean isAvailable();

  /**
   * Converts {@code docxFile} to PDF inside {@code outputDir}.
   *
   * @return the converted PDF path, or empty when conversion is unavailable
   */
  Optional<Path> convert(Path docxFile, Path outputDir) throws IOException;
}

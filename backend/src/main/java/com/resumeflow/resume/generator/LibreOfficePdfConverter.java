package com.resumeflow.resume.generator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LibreOffice headless DOCX→PDF conversion. Active only when {@code soffice}
 * is on the PATH; otherwise reports unavailable and callers keep the
 * same-format artifact instead of producing a worse PDF.
 */
@Component
public class LibreOfficePdfConverter implements DocxToPdfConverter {

  private static final Logger log = LoggerFactory.getLogger(LibreOfficePdfConverter.class);

  private final boolean available;

  public LibreOfficePdfConverter() {
    this.available = detect();
    if (available) {
      log.info("LibreOffice converter available for DOCX to PDF");
    } else {
      log.info("LibreOffice not found; DOCX downloads stay in DOCX format");
    }
  }

  private static boolean detect() {
    try {
      Process process = new ProcessBuilder("soffice", "--version")
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(15, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (Exception e) {
      log.debug("LibreOffice detection failed", e);
      return false;
    }
  }

  @Override
  public boolean isAvailable() {
    return available;
  }

  @Override
  public Optional<Path> convert(Path docxFile, Path outputDir) throws IOException {
    if (!available) {
      return Optional.empty();
    }
    try {
      Process process = new ProcessBuilder(
          "soffice", "--headless", "--convert-to", "pdf",
          "--outdir", outputDir.toString(), docxFile.toString())
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(120, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IOException("LibreOffice conversion timed out");
      }
      if (process.exitValue() != 0) {
        throw new IOException(
            "LibreOffice conversion failed: "
                + new String(process.getInputStream().readAllBytes()));
      }
      String base = docxFile.getFileName().toString().replaceAll("\\.[^.]+$", "");
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, base + ".pdf")) {
        for (Path pdf : stream) {
          return Optional.of(pdf);
        }
      }
      throw new IOException("LibreOffice produced no PDF output");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("LibreOffice conversion interrupted", e);
    }
  }
}

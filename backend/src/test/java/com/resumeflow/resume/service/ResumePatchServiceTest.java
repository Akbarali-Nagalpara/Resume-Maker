package com.resumeflow.resume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeflow.resume.generator.GeneratedArtifact;
import com.resumeflow.resume.parser.DocxResumeParser;
import com.resumeflow.resume.parser.ParsedResume;
import com.resumeflow.resume.parser.SampleResumes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** ResumePatchService patches the original document, preserving its template. */
class ResumePatchServiceTest {

  private final ResumePatchService patchService = new ResumePatchService(
      java.util.List.of(new com.resumeflow.resume.generator.DocxResumeGenerator()),
      new com.resumeflow.resume.generator.DocxToPdfConverter() {
        @Override
        public boolean isAvailable() {
          return false;
        }

        @Override
        public Optional<Path> convert(Path docxFile, Path outputDir) {
          return Optional.empty();
        }
      });

  @Test
  void patchesDocxContent(@TempDir Path directory) throws Exception {
    Path original = SampleResumes.docxSample(directory);
    ParsedResume parsed =
        new DocxResumeParser(new ObjectMapper()).parse(original, "checksum");

    Path output = directory.resolve("patched.docx");
    GeneratedArtifact artifact = patchService.patch(
        original,
        com.resumeflow.resume.parser.DocumentType.DOCX,
        new com.resumeflow.resume.parser.ResumeContentModel(
            parsed.content().personal(), "Patched summary.", parsed.content().skills(),
            parsed.content().experience(), parsed.content().projects(),
            parsed.content().education(), parsed.content().additionalSections()),
        parsed.template(),
        output);

    assertTrue(Files.size(output) > 0);
    assertEquals(64, artifact.sha256Hex().length());
    ParsedResume reparsed =
        new DocxResumeParser(new ObjectMapper()).parse(output, "checksum-2");
    assertEquals("Patched summary.", reparsed.content().summary());
  }

  @Test
  void reportsPdfConversionUnavailable() {
    assertTrue(!patchService.pdfConversionAvailable());
  }
}

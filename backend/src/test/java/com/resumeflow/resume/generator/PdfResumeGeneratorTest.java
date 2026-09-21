package com.resumeflow.resume.generator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeflow.resume.parser.ParsedResume;
import com.resumeflow.resume.parser.PdfResumeParser;
import com.resumeflow.resume.parser.SampleResumes;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** PDF generation quality: updated content re-rendered with captured geometry. */
class PdfResumeGeneratorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void rerendersUpdatedContent(@TempDir Path directory) throws Exception {
    Path original = SampleResumes.pdfSample(directory);
    ParsedResume parsed = new PdfResumeParser(objectMapper).parse(original, "checksum");
    var content = new com.resumeflow.resume.parser.ResumeContentModel(
        new com.resumeflow.resume.parser.PersonalInfo("Maya Chen", "Staff Engineer",
            "maya.chen@example.com", null, "San Francisco, CA", null),
        "Updated summary.", parsed.content().skills(), parsed.content().experience(),
        parsed.content().projects(), parsed.content().education(),
        parsed.content().additionalSections());

    Path output = directory.resolve("generated.pdf");
    new PdfResumeGenerator(new LayoutPdfRenderer()).generate(original, content,
        parsed.template(), output);

    assertTrue(Files.size(output) > 0);
    try (PDDocument document = Loader.loadPDF(output.toFile())) {
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Maya Chen"));
      assertTrue(text.contains("Staff Engineer"));
      assertTrue(text.contains("Updated summary."));
      // Captured page geometry is reused (LETTER width/height).
      assertTrue(Math.abs(document.getPage(0).getMediaBox().getWidth() - 612) < 1);
    }
  }
}

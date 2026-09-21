package com.resumeflow.resume.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class PdfResumeParserTest {

  private final PdfResumeParser parser = new PdfResumeParser(new ObjectMapper());

  @Test
  void extractsContentAndGeometry(@TempDir Path directory) throws Exception {
    Path file = SampleResumes.pdfSample(directory);

    ParsedResume parsed = parser.parse(file, "checksum-pdf");

    assertEquals(DocumentType.PDF, parsed.documentType());
    assertEquals("Maya Chen", parsed.content().personal().name());
    assertTrue(parsed.content().summary().contains("Product-minded"));
    assertEquals(4, parsed.content().skills().size());
    assertEquals(1, parsed.content().experience().size());
    assertEquals("Senior Product Engineer", parsed.content().experience().get(0).role());

    assertEquals("checksum-pdf", parsed.template().sourceChecksum());
    assertTrue(parsed.template().page().widthPoints() > 600);
    assertTrue(parsed.template().properties().get("pageCount").asInt() >= 1);
    assertTrue(parsed.template().properties().has("fonts"));
    assertTrue(parsed.template().properties().has("blocks"));
  }
}

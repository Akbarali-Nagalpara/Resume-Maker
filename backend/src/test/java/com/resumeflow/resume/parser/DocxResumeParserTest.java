package com.resumeflow.resume.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class DocxResumeParserTest {

  private final DocxResumeParser parser = new DocxResumeParser(new ObjectMapper());

  @Test
  void extractsContentAndTemplate(@TempDir Path directory) throws Exception {
    Path file = SampleResumes.docxSample(directory);

    ParsedResume parsed = parser.parse(file, "checksum-docx");

    assertEquals(DocumentType.DOCX, parsed.documentType());
    assertEquals("Maya Chen", parsed.content().personal().name());
    assertEquals("Senior Product Engineer", parsed.content().personal().title());
    assertEquals("maya.chen@example.com", parsed.content().personal().email());
    assertTrue(parsed.content().summary().contains("Product-minded"));
    assertEquals(4, parsed.content().skills().size());
    assertEquals(2, parsed.content().experience().size());
    assertEquals("Senior Product Engineer", parsed.content().experience().get(0).role());
    assertEquals("Northstar Labs", parsed.content().experience().get(0).company());
    assertEquals(2, parsed.content().experience().get(0).bullets().size());
    assertEquals(1, parsed.content().projects().size());
    assertEquals("Open Metrics Kit", parsed.content().projects().get(0).name());
    assertEquals(1, parsed.content().education().size());

    assertEquals("checksum-docx", parsed.template().sourceChecksum());
    assertTrue(parsed.template().page().widthPoints() > 0);
    assertTrue(parsed.template().properties().has("paragraphs"));
    assertTrue(parsed.template().mappings().has("experience[0]"));

    var keys = parsed.sections().stream().map(ParsedResume.SectionIndexEntry::key).toList();
    assertEquals(java.util.List.of("summary", "skills", "experience", "projects", "education"),
        keys);
  }
}

package com.resumeflow.resume.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Template/layout extraction across representative DOCX shapes. */
class DocxTemplateVariantsTest {

  private final DocxResumeParser parser = new DocxResumeParser(new ObjectMapper());

  @Test
  void detectsTwoColumnsAndTables(@TempDir Path directory) throws Exception {
    Path file = SampleResumes.docxSample(directory, "two-col.docx", true, false, true);

    ParsedResume parsed = parser.parse(file, "checksum");

    assertEquals(2, parsed.template().properties().get("columns").asInt());
    assertEquals(1, parsed.template().properties().get("tables").asInt());
    assertEquals(0, parsed.template().properties().get("images").asInt());
    // Content extraction is unaffected by layout variants.
    assertEquals("Maya Chen", parsed.content().personal().name());
    assertEquals(2, parsed.content().experience().size());
  }

  @Test
  void detectsHeadersAndFooters(@TempDir Path directory) throws Exception {
    Path file = SampleResumes.docxSample(directory, "hf.docx", false, false, false, true);

    ParsedResume parsed = parser.parse(file, "checksum");

    assertTrue(parsed.template().properties().get("hasHeader").asBoolean());
    assertTrue(parsed.template().properties().get("hasFooter").asBoolean());
    assertEquals("Maya Chen", parsed.content().personal().name());
  }

  @Test
  void handlesMultiPageDocuments(@TempDir Path directory) throws Exception {
    Path file = SampleResumes.docxSample(directory, "multi.docx", false, true, false);

    ParsedResume parsed = parser.parse(file, "checksum");

    assertTrue(parsed.template().properties().get("paragraphCount").asInt() > 60);
    assertEquals("Maya Chen", parsed.content().personal().name());
  }

  @Test
  void mapsSectionVariations(@TempDir Path directory) throws Exception {
    // "Work Experience" must map to the experience key (covered by the
    // fixture); unknown sections land in additionalSections, never dropped.
    Path file = SampleResumes.docxSample(directory, "v.docx", false, false, false);

    ParsedResume parsed = parser.parse(file, "checksum");

    var keys = parsed.sections().stream().map(ParsedResume.SectionIndexEntry::key).toList();
    assertTrue(keys.contains("experience"));
  }
}

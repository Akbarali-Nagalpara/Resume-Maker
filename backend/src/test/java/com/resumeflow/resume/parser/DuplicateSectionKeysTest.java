package com.resumeflow.resume.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * Regression: repeated headings must never produce duplicate section keys
 * (previously violated uq_resume_sections_resume_key, e.g. "custom-mysql"
 * twice). Custom repeats get deterministic suffixes; known repeats merge.
 */
class DuplicateSectionKeysTest {

  private final DocxResumeParser parser = new DocxResumeParser(new ObjectMapper());

  @Test
  void duplicateCustomHeadingsGetSuffixedKeys(@TempDir Path directory) throws Exception {
    Path file = duplicateHeadingsDocx(directory);

    ParsedResume parsed = parser.parse(file, "checksum");

    List<String> keys =
        parsed.sections().stream().map(ParsedResume.SectionIndexEntry::key).toList();
    assertTrue(keys.contains("custom-mysql"));
    assertTrue(keys.contains("custom-mysql-2"));
    assertEquals(keys.size(), keys.stream().distinct().count());
    // Both custom sections survive with their own content.
    assertEquals(2,
        parsed.content().additionalSections().stream()
            .filter(section -> section.key().startsWith("custom-mysql"))
            .count());
  }

  @Test
  void duplicateKnownHeadingsMerge(@TempDir Path directory) throws Exception {
    Path file = duplicateHeadingsDocx(directory);

    ParsedResume parsed = parser.parse(file, "checksum");

    long experienceKeys = parsed.sections().stream()
        .map(ParsedResume.SectionIndexEntry::key)
        .filter("experience"::equals)
        .count();
    assertEquals(1, experienceKeys);
    // Both experience blocks contribute items.
    assertEquals(2, parsed.content().experience().size());
  }

  private static Path duplicateHeadingsDocx(Path directory) throws Exception {
    XWPFDocument document = new XWPFDocument();
    paragraph(document, "Maya Chen", null);
    paragraph(document, "EXPERIENCE", "Heading1");
    paragraph(document, "Senior Role @ Northstar");
    paragraph(document, "MySQL", "Heading1");
    paragraph(document, "Version 8.");
    paragraph(document, "EXPERIENCE", "Heading1");
    paragraph(document, "Junior Role @ Fieldwork");
    paragraph(document, "MySQL", "Heading1");
    paragraph(document, "Version 5.");
    Path file = directory.resolve("duplicates.docx");
    try (OutputStream out = Files.newOutputStream(file)) {
      document.write(out);
    }
    document.close();
    return file;
  }

  private static void paragraph(XWPFDocument document, String text) {
    paragraph(document, text, null);
  }

  private static void paragraph(XWPFDocument document, String text, String style) {
    XWPFParagraph paragraph = document.createParagraph();
    if (style != null) {
      paragraph.setStyle(style);
    }
    XWPFRun run = paragraph.createRun();
    run.setText(text);
  }
}

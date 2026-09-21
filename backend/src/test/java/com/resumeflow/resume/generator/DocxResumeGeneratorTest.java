package com.resumeflow.resume.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeflow.resume.parser.DocxResumeParser;
import com.resumeflow.resume.parser.ParsedResume;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.SampleResumes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * Generation quality: edited content lands in the output while paragraph
 * styles, tables and sections of the original template are preserved.
 */
class DocxResumeGeneratorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DocxResumeParser parser = new DocxResumeParser(objectMapper);
  private final DocxResumeGenerator generator = new DocxResumeGenerator();

  @Test
  void preservesTemplateWhileUpdatingContent(@TempDir Path directory) throws Exception {
    Path original = SampleResumes.docxSample(directory);
    List<String> styleIdsBefore = paragraphStyleIds(original);
    ParsedResume parsed = parser.parse(original, "checksum");

    var personal = new com.resumeflow.resume.parser.PersonalInfo("Maya Chen",
        "Staff Product Engineer", parsed.content().personal().email(),
        parsed.content().personal().phone(), parsed.content().personal().location(),
        parsed.content().personal().website());
    List<String> skills = new ArrayList<>(parsed.content().skills());
    skills.add("Docker");
    List<com.resumeflow.resume.parser.ExperienceItem> experience =
        new ArrayList<>(parsed.content().experience());
    var first = experience.get(0);
    List<String> bullets = new ArrayList<>(first.bullets());
    bullets.add("Shipped design system v2.");
    experience.set(0,
        new com.resumeflow.resume.parser.ExperienceItem(first.id(), first.role(),
            first.company(), first.location(), first.dates(), bullets));
    ResumeContentModel updated = new ResumeContentModel(personal,
        "Updated summary.", skills, experience, parsed.content().projects(),
        parsed.content().education(), parsed.content().additionalSections());

    Path output = directory.resolve("generated.docx");
    GeneratedArtifact artifact = generator.generate(original, updated, parsed.template(), output);

    assertTrue(Files.size(output) > 0);
    assertEquals(64, artifact.sha256Hex().length());

    ParsedResume reparsed = parser.parse(output, "checksum-2");
    assertEquals("Updated summary.", reparsed.content().summary());
    assertTrue(reparsed.content().skills().contains("Docker"));
    assertEquals("Maya Chen", reparsed.content().personal().name());
    // Template preservation: the style sequence is unchanged apart from the
    // single appended bullet paragraph (inserted after exp-1's range at index
    // 12 for this fixture). Appended bullets must not shift later paragraphs.
    List<String> outputStyleIds = paragraphStyleIds(output);
    List<String> withoutAppended = new ArrayList<>(outputStyleIds);
    // The appended bullet copies its anchor paragraph's style (unstyled body
    // text in this fixture, like many real resumes with literal markers).
    withoutAppended.remove(12);
    assertEquals(styleIdsBefore, withoutAppended);
    List<String> outputTexts = paragraphTexts(output);
    assertTrue(outputTexts.contains("Updated summary."));
    assertTrue(outputTexts.contains("TypeScript | React | Java | Spring Boot | Docker"));
    assertTrue(outputTexts.contains("Experience"));
    int shipped = outputTexts.indexOf("\u2022 Shipped design system v2.");
    int nextRole = outputTexts.indexOf("Software Engineer @ Fieldwork Studio");
    assertTrue(shipped > 0);
    assertTrue(nextRole > shipped);
  }

  @Test
  void appendsNewItemsWithoutMovingExisting(@TempDir Path directory) throws Exception {
    Path original = SampleResumes.docxSample(directory);
    ParsedResume parsed = parser.parse(original, "checksum");
    List<com.resumeflow.resume.parser.ExperienceItem> experience =
        new ArrayList<>(parsed.content().experience());
    experience.add(new com.resumeflow.resume.parser.ExperienceItem("exp-99", "Intern", "Acme",
        null, "2018", java.util.List.of("Learned a lot.")));
    ResumeContentModel updated = new ResumeContentModel(parsed.content().personal(),
        parsed.content().summary(), parsed.content().skills(), experience,
        parsed.content().projects(), parsed.content().education(),
        parsed.content().additionalSections());

    Path output = directory.resolve("generated-new-item.docx");
    generator.generate(original, updated, parsed.template(), output);

    ParsedResume reparsed = parser.parse(output, "checksum-2");
    assertEquals(3, reparsed.content().experience().size());
    assertEquals("Intern", reparsed.content().experience().get(2).role());
    assertEquals("Acme", reparsed.content().experience().get(2).company());
    // Pre-existing items keep their exact paragraphs.
    assertEquals("Senior Product Engineer", reparsed.content().experience().get(0).role());
    assertEquals("Software Engineer", reparsed.content().experience().get(1).role());
  }

  private static List<String> paragraphStyleIds(Path file) throws Exception {
    try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
      List<String> styles = new ArrayList<>();
      for (XWPFParagraph paragraph : document.getParagraphs()) {
        styles.add(String.valueOf(paragraph.getStyle()));
      }
      return styles;
    }
  }

  private static List<String> paragraphTexts(Path file) throws Exception {
    try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
      List<String> texts = new ArrayList<>();
      for (XWPFParagraph paragraph : document.getParagraphs()) {
        texts.add(paragraph.getText());
      }
      return texts;
    }
  }
}

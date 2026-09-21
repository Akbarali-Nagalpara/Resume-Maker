package com.resumeflow.resume.generator;

import tools.jackson.databind.JsonNode;
import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.SectionClassifier;
import com.resumeflow.resume.parser.TemplateMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DOCX generator with strict template preservation. The original document is
 * opened read-only in memory; only the text of mapped content paragraphs is
 * replaced. Styles, numbering, tables, sections, headers, footers, images and
 * relationships are carried over untouched because the original package is
 * the generation base.
 */
@Component
public class DocxResumeGenerator implements ResumeGenerator {

  private static final Logger log = LoggerFactory.getLogger(DocxResumeGenerator.class);

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.DOCX;
  }

  @Override
  public GeneratedArtifact generate(
      Path originalFile,
      ResumeContentModel content,
      TemplateMetadata template,
      Path outputFile) {
    try (XWPFDocument document = new XWPFDocument(Files.newInputStream(originalFile))) {
      Map<String, List<XWPFParagraph>> byNode = indexParagraphs(document);
      applyPersonal(content, template, byNode, document);
      applySummary(content, template, byNode, document);
      applySkills(content, template, byNode, document);
      applyExperience(content, template, byNode, document);
      applyProjects(content, template, byNode, document);
      applyEducation(content, template, byNode, document);
      try (var out = Files.newOutputStream(outputFile)) {
        document.write(out);
      }
      return ArtifactChecksums.of(outputFile,
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    } catch (ResumeParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ResumeParseException("Cannot generate DOCX resume", e);
    }
  }

  private Map<String, List<XWPFParagraph>> indexParagraphs(XWPFDocument document) {
    Map<String, List<XWPFParagraph>> index = new HashMap<>();
    List<XWPFParagraph> paragraphs = document.getParagraphs();
    for (int i = 0; i < paragraphs.size(); i++) {
      index.computeIfAbsent("p" + i, key -> new ArrayList<>()).add(paragraphs.get(i));
    }
    return index;
  }

  private void applyPersonal(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.personal() == null) {
      return;
    }
    setParagraphText(byNode.get("p0"), content.personal().name());
    // Contact/title lines beyond the name have no stable mapping; they are
    // intentionally left untouched rather than guessed.
  }

  private void applySummary(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    List<String> nodeIds = mappingIds(template, "summary");
    if (nodeIds.isEmpty() || content.summary() == null) {
      return;
    }
    String[] lines = content.summary().split("\n");
    for (int i = 0; i < nodeIds.size(); i++) {
      List<XWPFParagraph> targets = byNode.get(nodeIds.get(i));
      if (targets == null || targets.isEmpty()) {
        continue;
      }
      setParagraphText(targets, i < lines.length ? lines[i].trim() : "");
    }
  }

  private void applySkills(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    List<String> nodeIds = mappingIds(template, "skills");
    if (nodeIds.isEmpty() || content.skills() == null) {
      return;
    }
    String joined = String.join(" | ", content.skills());
    setParagraphText(byNode.get(nodeIds.get(0)), joined);
    for (int i = 1; i < nodeIds.size(); i++) {
      setParagraphText(byNode.get(nodeIds.get(i)), "");
    }
  }

  private void applyExperience(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.experience() == null) {
      return;
    }
    // Experience edits target only the mapped paragraph range; item counts
    // are preserved to avoid reflowing the original layout.
    for (int i = 0; i < content.experience().size(); i++) {
      var item = content.experience().get(i);
      List<String> nodeIds = mappingIds(template, "experience[" + i + "]");
      if (nodeIds.isEmpty()) {
        log.warn("No template mapping for experience item {}", i);
        continue;
      }
      List<String> replacement = new ArrayList<>();
      String heading = item.role() == null ? "" : item.role();
      if (item.company() != null && !item.company().isBlank()) {
        heading += " @ " + item.company();
      }
      replacement.add(heading);
      if (item.dates() != null && !item.dates().isBlank()) {
        replacement.add(item.dates());
      }
      if (item.bullets() != null) {
        replacement.addAll(item.bullets());
      }
      replaceRange(byNode, nodeIds, replacement, document);
    }
  }

  private void applyProjects(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.projects() == null) {
      return;
    }
    for (int i = 0; i < content.projects().size(); i++) {
      var item = content.projects().get(i);
      List<String> nodeIds = mappingIds(template, "projects[" + i + "]");
      if (nodeIds.isEmpty()) {
        log.warn("No template mapping for project item {}", i);
        continue;
      }
      List<String> replacement = new ArrayList<>();
      replacement.add(item.name() == null ? "" : item.name());
      if (item.description() != null && !item.description().isBlank()) {
        replacement.add(item.description());
      }
      if (item.stack() != null && !item.stack().isBlank()) {
        replacement.add(item.stack());
      }
      replaceRange(byNode, nodeIds, replacement, document);
    }
  }

  private void applyEducation(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.education() == null) {
      return;
    }
    for (int i = 0; i < content.education().size(); i++) {
      var item = content.education().get(i);
      List<String> nodeIds = mappingIds(template, "education[" + i + "]");
      if (nodeIds.isEmpty()) {
        continue;
      }
      List<String> replacement = new ArrayList<>();
      if (item.degree() != null) {
        replacement.add(item.degree());
      }
      if (item.school() != null) {
        replacement.add(item.school());
      }
      if (item.dates() != null) {
        replacement.add(item.dates());
      }
      replaceRange(byNode, nodeIds, replacement, document);
    }
    appendNewItems(content, template, byNode, document);
  }

  /**
   * Appends content items that have no template mapping (added by the user
   * after parsing) at the end of their section, copying the styles of
   * analogous paragraphs. No existing paragraph is moved or restyled.
   */
  private void appendNewItems(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    appendNewExperience(content, template, byNode, document);
    appendNewProjects(content, template, byNode, document);
    appendNewEducation(content, template, byNode, document);
  }

  private void appendNewExperience(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.experience() == null) {
      return;
    }
    List<String> sectionNodes = mappingIds(template, "experience");
    int mapped = countIndexedMappings(template, "experience");
    if (sectionNodes.isEmpty()) {
      return;
    }
    XWPFParagraph anchor = lastParagraph(byNode, sectionNodes);
    XWPFParagraph roleStyle = firstParagraph(byNode, sectionNodes);
    XWPFParagraph bulletStyle = bulletParagraph(byNode, sectionNodes);
    for (int i = mapped; i < content.experience().size(); i++) {
      var item = content.experience().get(i);
      String heading = item.role() == null ? "" : item.role();
      if (item.company() != null && !item.company().isBlank()) {
        heading += " @ " + item.company();
      }
      anchor = appendStyled(anchor, roleStyle, heading, document);
      if (item.dates() != null && !item.dates().isBlank()) {
        anchor = appendStyled(anchor, roleStyle, item.dates(), document);
      }
      if (item.bullets() != null) {
        for (String bullet : item.bullets()) {
          anchor = appendStyled(anchor, bulletStyle != null ? bulletStyle : roleStyle,
              bullet, document);
        }
      }
    }
  }

  private void appendNewProjects(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.projects() == null) {
      return;
    }
    List<String> sectionNodes = mappingIds(template, "projects");
    int mapped = countIndexedMappings(template, "projects");
    if (sectionNodes.isEmpty()) {
      return;
    }
    XWPFParagraph anchor = lastParagraph(byNode, sectionNodes);
    XWPFParagraph first = firstParagraph(byNode, sectionNodes);
    for (int i = mapped; i < content.projects().size(); i++) {
      var item = content.projects().get(i);
      anchor = appendStyled(anchor, first, item.name() == null ? "" : item.name(), document);
      if (item.description() != null && !item.description().isBlank()) {
        anchor = appendStyled(anchor, first, item.description(), document);
      }
      if (item.stack() != null && !item.stack().isBlank()) {
        anchor = appendStyled(anchor, first, item.stack(), document);
      }
    }
  }

  private void appendNewEducation(
      ResumeContentModel content,
      TemplateMetadata template,
      Map<String, List<XWPFParagraph>> byNode,
      XWPFDocument document) {
    if (content.education() == null) {
      return;
    }
    List<String> sectionNodes = mappingIds(template, "education");
    int mapped = countIndexedMappings(template, "education");
    if (sectionNodes.isEmpty()) {
      return;
    }
    XWPFParagraph anchor = lastParagraph(byNode, sectionNodes);
    XWPFParagraph first = firstParagraph(byNode, sectionNodes);
    for (int i = mapped; i < content.education().size(); i++) {
      var item = content.education().get(i);
      anchor = appendStyled(anchor, first, item.degree() == null ? "" : item.degree(), document);
      if (item.school() != null && !item.school().isBlank()) {
        anchor = appendStyled(anchor, first, item.school(), document);
      }
      if (item.dates() != null && !item.dates().isBlank()) {
        anchor = appendStyled(anchor, first, item.dates(), document);
      }
    }
  }

  private XWPFParagraph appendStyled(
      XWPFParagraph anchor, XWPFParagraph styleSource, String text, XWPFDocument document) {
    XWPFParagraph created = insertAfter(anchor, document);
    copyStyle(styleSource, created);
    setParagraphText(List.of(created), text);
    return created;
  }

  private int countIndexedMappings(TemplateMetadata template, String base) {
    JsonNode mappings = template.mappings();
    if (mappings == null) {
      return 0;
    }
    int count = 0;
    while (mappings.has(base + "[" + count + "]")) {
      count++;
    }
    return count;
  }

  private XWPFParagraph firstParagraph(
      Map<String, List<XWPFParagraph>> byNode, List<String> nodeIds) {
    for (String id : nodeIds) {
      List<XWPFParagraph> targets = byNode.get(id);
      if (targets != null && !targets.isEmpty()) {
        return targets.get(0);
      }
    }
    throw new ResumeParseException("Template mapping points to missing paragraphs", null);
  }

  private XWPFParagraph lastParagraph(
      Map<String, List<XWPFParagraph>> byNode, List<String> nodeIds) {
    for (int i = nodeIds.size() - 1; i >= 0; i--) {
      List<XWPFParagraph> targets = byNode.get(nodeIds.get(i));
      if (targets != null && !targets.isEmpty()) {
        return targets.get(targets.size() - 1);
      }
    }
    throw new ResumeParseException("Template mapping points to missing paragraphs", null);
  }

  private XWPFParagraph bulletParagraph(
      Map<String, List<XWPFParagraph>> byNode, List<String> nodeIds) {
    for (String id : nodeIds) {
      List<XWPFParagraph> targets = byNode.get(id);
      if (targets == null) {
        continue;
      }
      for (XWPFParagraph paragraph : targets) {
        if (SectionClassifier.isBullet(
            paragraph.getText() == null ? "" : paragraph.getText())) {
          return paragraph;
        }
      }
    }
    return null;
  }

  private void replaceRange(
      Map<String, List<XWPFParagraph>> byNode,
      List<String> nodeIds,
      List<String> replacement,
      XWPFDocument document) {
    for (int i = 0; i < nodeIds.size(); i++) {
      List<XWPFParagraph> targets = byNode.get(nodeIds.get(i));
      if (targets == null || targets.isEmpty()) {
        continue;
      }
      setParagraphText(targets, i < replacement.size() ? replacement.get(i) : "");
    }
    if (replacement.size() > nodeIds.size()) {
      XWPFParagraph anchor = byNode.get(nodeIds.get(nodeIds.size() - 1)).get(0);
      XWPFParagraph styleSource = anchor;
      boolean anchorBulleted = SectionClassifier.isBullet(
          anchor.getText() == null ? "" : anchor.getText());
      for (int i = nodeIds.size(); i < replacement.size(); i++) {
        anchor = insertAfter(anchor, document);
        copyStyle(styleSource, anchor);
        String line = replacement.get(i);
        if (anchorBulleted && line != null && !line.isBlank()
            && !SectionClassifier.isBullet(line)) {
          line = "\u2022 " + line;
        }
        setParagraphText(List.of(anchor), line);
      }
    }
  }

  /** Inserts a new paragraph immediately after the anchor paragraph. */
  private static XWPFParagraph insertAfter(XWPFParagraph anchor, XWPFDocument document) {
    var body = document.getDocument().getBody();
    int index = body.getPList().indexOf(anchor.getCTP());
    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP inserted =
        index == -1 ? body.addNewP() : body.insertNewP(index + 1);
    return new XWPFParagraph(inserted, document);
  }

  private List<String> mappingIds(TemplateMetadata template, String key) {
    JsonNode mappings = template.mappings();
    if (mappings == null || !mappings.has(key)) {
      return List.of();
    }
    String raw = mappings.get(key).asText("");
    if (raw.isBlank()) {
      return List.of();
    }
    List<String> ids = new ArrayList<>();
    for (String part : raw.split(",")) {
      String id = part.trim();
      if (!id.isBlank()) {
        ids.add(id);
      }
    }
    return ids;
  }

  /**
   * Replaces paragraph text while keeping every style: runs are rebuilt from
   * the first run's formatting and paragraph properties (including numbering)
   * are left untouched. A literal bullet marker present in the original
   * paragraph is restored so list styling survives the round-trip.
   */
  static void setParagraphText(List<XWPFParagraph> targets, String text) {
    if (targets == null || targets.isEmpty()) {
      return;
    }
    for (XWPFParagraph paragraph : targets) {
      String original = paragraph.getText();
      String replacement = text == null ? "" : text;
      if (!replacement.isBlank()
          && original != null
          && SectionClassifier.isBullet(original)
          && !SectionClassifier.isBullet(replacement)) {
        replacement = "\u2022 " + replacement;
      }
      List<XWPFRun> runs = paragraph.getRuns();
      boolean bold = false;
      Double size = null;
      String color = null;
      String font = null;
      if (!runs.isEmpty()) {
        XWPFRun first = runs.get(0);
        bold = first.isBold();
        size = first.getFontSizeAsDouble();
        color = first.getColor();
        font = first.getFontFamily();
      }
      for (int i = runs.size() - 1; i >= 0; i--) {
        paragraph.removeRun(i);
      }
      XWPFRun run = paragraph.createRun();
      run.setBold(bold);
      if (size != null) {
        run.setFontSize(size);
      }
      if (color != null) {
        run.setColor(color);
      }
      if (font != null) {
        run.setFontFamily(font);
      }
      run.setText(replacement);
    }
  }

  private static void copyStyle(XWPFParagraph source, XWPFParagraph target) {
    if (source.getStyle() != null) {
      target.setStyle(source.getStyle());
    }
    if (source.getAlignment() != null) {
      target.setAlignment(source.getAlignment());
    }
    if (source.getNumID() != null) {
      target.setNumID(source.getNumID());
    }
  }
}

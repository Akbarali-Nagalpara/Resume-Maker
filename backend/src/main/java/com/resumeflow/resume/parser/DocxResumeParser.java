package com.resumeflow.resume.parser;

import com.resumeflow.exception.ResumeParseException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * DOCX parser built on Apache POI/OOXML. Extracts editable content with
 * paragraph-level source mappings and captures page geometry, styles,
 * tables, headers/footers and numbering as template metadata.
 *
 * <p>Section detection is heuristic: explicit Heading styles and known
 * section keywords are reliable; ALL-CAPS titles are only treated as
 * headings after the first real section starts, so contact blocks stay
 * in the preamble.
 */
@Component
public class DocxResumeParser implements ResumeParser {

  private static final Logger log = LoggerFactory.getLogger(DocxResumeParser.class);

  private final ObjectMapper objectMapper;

  public DocxResumeParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.DOCX;
  }

  @Override
  public ParsedResume parse(Path file, String sourceChecksum) {
    try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
      List<ParagraphView> paragraphs = readParagraphs(document);
      return assemble(paragraphs, document, sourceChecksum);
    } catch (ResumeParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ResumeParseException("Cannot parse DOCX resume", e);
    }
  }

  private List<ParagraphView> readParagraphs(XWPFDocument document) {
    List<ParagraphView> views = new ArrayList<>();
    List<XWPFParagraph> body = document.getParagraphs();
    for (int i = 0; i < body.size(); i++) {
      XWPFParagraph paragraph = body.get(i);
      String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
      views.add(new ParagraphView("p" + i, text, styleOf(paragraph), alignmentOf(paragraph),
          spacingOf(paragraph), runStyleOf(paragraph), paragraph.getNumID() != null));
    }
    return views;
  }

  private ParsedResume assemble(
      List<ParagraphView> paragraphs, XWPFDocument document, String sourceChecksum) {
    List<Block> blocks = splitBlocks(paragraphs);
    int firstHeading = firstHeadingIndex(blocks);

    List<String> preamble = new ArrayList<>();
    for (Block block : blocks.subList(0, firstHeading)) {
      preamble.addAll(block.lines());
    }
    PersonalInfo personal = parsePersonal(preamble);

    // Section body = heading block's own lines plus following plain blocks.
    Map<String, SectionBody> sections = new LinkedHashMap<>();
    Map<String, String> titles = new LinkedHashMap<>();
    List<ParsedResume.SectionIndexEntry> index = new ArrayList<>();
    List<TemplateMetadata.SectionAnchor> anchors = new ArrayList<>();
    ObjectNode mappings = objectMapper.createObjectNode();
    int position = 0;
    for (int i = firstHeading; i < blocks.size(); i++) {
      Block block = blocks.get(i);
      if (!block.heading()) {
        continue;
      }
      String key = SectionClassifier.classifyHeading(block.title()).orElseGet(
          () -> "custom-" + block.title().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
      List<String> lines = new ArrayList<>(block.lines());
      List<String> nodeIds = new ArrayList<>(block.nodeIds());
      // Content mappings reference body paragraphs only: the heading
      // paragraph itself is template, never overwritten by generation.
      List<String> contentNodeIds = new ArrayList<>(block.nodeIds().stream().skip(1).toList());
      for (int j = i + 1; j < blocks.size() && !blocks.get(j).heading(); j++) {
        lines.addAll(blocks.get(j).lines());
        nodeIds.addAll(blocks.get(j).nodeIds());
        contentNodeIds.addAll(blocks.get(j).nodeIds());
      }
      sections.put(key, new SectionBody(lines, contentNodeIds));
      titles.put(key, block.title());
      index.add(new ParsedResume.SectionIndexEntry(key, block.title(), position));
      anchors.add(
          new TemplateMetadata.SectionAnchor(key, block.title(), position,
              String.join(",", nodeIds)));
      mappings.put(key, String.join(",", contentNodeIds));
      position++;
    }

    ResumeContentModel content = buildContent(personal, sections, titles, mappings);
    TemplateMetadata template = buildTemplate(document, paragraphs, anchors, mappings,
        sourceChecksum);    return new ParsedResume(DocumentType.DOCX, content, template, index);
  }

  private PersonalInfo parsePersonal(List<String> lines) {
    String name = lines.isEmpty() ? "Unnamed" : lines.get(0);
    String title = null;
    String email = null;
    String phone = null;
    String location = null;
    String website = null;
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.contains("@") || SectionClassifier.parseContactLine(line).phone() != null) {
        SectionClassifier.ContactParts parts = SectionClassifier.parseContactLine(line);
        if (parts.email() != null) {
          email = parts.email();
        }
        if (parts.phone() != null) {
          phone = parts.phone();
        }
        if (parts.website() != null) {
          website = parts.website();
        }
        if (parts.location() != null) {
          location = parts.location();
        }
      } else if (title == null) {
        title = line;
      } else if (website == null && line.matches("(?i).*(\\.com|\\.dev|\\.io|\\.me|http).*")) {
        website = line;
      } else if (location == null) {
        location = line;
      }
    }
    return new PersonalInfo(name, title, email, phone, location, website);
  }

  private ResumeContentModel buildContent(
      PersonalInfo personal,
      Map<String, SectionBody> sections,
      Map<String, String> titles,
      ObjectNode mappings) {
    SectionBody summary = sections.getOrDefault("summary", new SectionBody(List.of(), List.of()));
    List<String> skills = new ArrayList<>();
    SectionBody skillsBody = sections.getOrDefault("skills", new SectionBody(List.of(), List.of()));
    for (String line : skillsBody.lines()) {
      skills.addAll(SectionClassifier.splitSkills(line));
    }
    List<ExperienceItem> experience = parseExperience(
        sections.getOrDefault("experience", new SectionBody(List.of(), List.of())), mappings);
    List<ProjectItem> projects = parseProjects(
        sections.getOrDefault("projects", new SectionBody(List.of(), List.of())), mappings);
    List<EducationItem> education = parseEducation(
        sections.getOrDefault("education", new SectionBody(List.of(), List.of())), mappings);
    List<ResumeContentModel.AdditionalSection> additional = new ArrayList<>();
    sections.forEach((key, body) -> {
      if (!List.of("summary", "skills", "experience", "projects", "education").contains(key)) {
        additional.add(new ResumeContentModel.AdditionalSection(key,
            titles.getOrDefault(key, key), body.lines()));
      }
    });
    mappings.put("personal.name", "p0");
    return new ResumeContentModel(personal, String.join("\n", summary.lines()),
        skills.stream().distinct().toList(), experience, projects, education, additional);
  }

  /**
   * Parses experience items, recording each item's paragraph range so the
   * generator rewrites items in place. Lines and node ids are
   * paragraph-aligned.
   */
  private List<ExperienceItem> parseExperience(SectionBody body, ObjectNode mappings) {
    List<Integer> kept = new ArrayList<>();
    for (int i = 0; i < body.lines().size(); i++) {
      if (!body.lines().get(i).isBlank()) {
        kept.add(i);
      }
    }
    List<String> keptLines = kept.stream().map(body.lines()::get).toList();
    List<List<String>> groups = groupExperienceLines(keptLines);
    List<ExperienceItem> items = new ArrayList<>();
    int consumed = 0;
    for (int i = 0; i < groups.size(); i++) {
      List<String> group = groups.get(i);
      List<String> groupNodes = new ArrayList<>();
      for (int j = 0; j < group.size(); j++) {
        groupNodes.add(body.nodeIds().get(kept.get(consumed + j)));
      }
      consumed += group.size();
      mappings.put("experience[" + i + "]", String.join(",", groupNodes));
      items.add(toExperience(group, i));
    }
    return items;
  }

  /**
   * Groups item lines: a new item starts at a {@code role @ company} line, or
   * at a date-range line when the current group already holds bullets.
   */
  static List<List<String>> groupExperienceLines(List<String> lines) {
    List<List<String>> groups = new ArrayList<>();
    List<String> current = new ArrayList<>();
    for (String line : lines) {
      boolean roleLine = line.contains(" @ ");
      boolean nextDates = SectionClassifier.containsDateRange(line)
          && current.stream().anyMatch(SectionClassifier::isBullet);
      if ((roleLine || nextDates) && !current.isEmpty()) {
        groups.add(current);
        current = new ArrayList<>();
      }
      current.add(line);
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  private ExperienceItem toExperience(List<String> group, int counter) {
    String heading = group.get(0);
    String role = heading;
    String company = null;
    String[] split = heading.split("\\s+@\\s+|\\s+[—–-]\\s+|\\s*,\\s*", 2);
    if (split.length == 2) {
      role = split[0].trim();
      company = split[1].trim();
    }
    String dates = null;
    List<String> bullets = new ArrayList<>();
    for (int i = 1; i < group.size(); i++) {
      String line = group.get(i);
      if (dates == null && SectionClassifier.containsDateRange(line) && line.length() < 60) {
        dates = line;
      } else if (SectionClassifier.isBullet(line)) {
        bullets.add(SectionClassifier.stripBullet(line));
      } else if (!line.isBlank()) {
        bullets.add(line);
      }
    }
    if (company != null && company.matches("(?i).*((19|20)\\d{2}|present).*")) {
      dates = company;
      company = null;
    }
    return new ExperienceItem("exp-" + (counter + 1), role, company, null, dates, bullets);
  }

  private List<ProjectItem> parseProjects(SectionBody body, ObjectNode mappings) {
    List<ProjectItem> items = new ArrayList<>();
    int counter = 0;
    for (List<Integer> group : groupBlockIndexes(body.lines())) {
      List<String> groupLines = group.stream().map(body.lines()::get).toList();
      if (groupLines.isEmpty()) {
        continue;
      }
      String name = groupLines.get(0);
      String stack = null;
      List<String> description = new ArrayList<>();
      for (int i = 1; i < groupLines.size(); i++) {
        String line = SectionClassifier.isBullet(groupLines.get(i))
            ? SectionClassifier.stripBullet(groupLines.get(i))
            : groupLines.get(i);
        if (i == groupLines.size() - 1 && line.matches("(?i).*[·|/,].*") && line.length() < 120) {
          stack = line;
        } else {
          description.add(line);
        }
      }
      mappings.put("projects[" + counter + "]",
          String.join(",", group.stream().map(body.nodeIds()::get).toList()));
      items.add(new ProjectItem("project-" + (++counter), name,
          String.join(" ", description), stack));
    }
    return items;
  }

  private List<EducationItem> parseEducation(SectionBody body, ObjectNode mappings) {
    List<EducationItem> items = new ArrayList<>();
    int counter = 0;
    for (List<Integer> group : groupBlockIndexes(body.lines())) {
      List<String> groupLines = group.stream().map(body.lines()::get).toList();
      if (groupLines.isEmpty()) {
        continue;
      }
      mappings.put("education[" + counter + "]",
          String.join(",", group.stream().map(body.nodeIds()::get).toList()));
      items.add(new EducationItem("education-" + (++counter), groupLines.get(0),
          groupLines.size() > 1 ? groupLines.get(1) : null,
          groupLines.size() > 2 ? groupLines.get(groupLines.size() - 1) : null));
    }
    return items;
  }

  /**
   * Splits section lines back into blank-line separated groups. The splitter
   * dropped blank lines, so groups are recovered from bullet runs: a
   * non-bullet line following bullets starts a new group.
   */
  private static List<List<Integer>> groupBlockIndexes(List<String> lines) {
    List<List<Integer>> groups = new ArrayList<>();
    List<Integer> current = new ArrayList<>();
    boolean seenBullet = false;
    for (int i = 0; i < lines.size(); i++) {
      boolean bullet = SectionClassifier.isBullet(lines.get(i));
      if (!bullet && seenBullet && !current.isEmpty()) {
        groups.add(current);
        current = new ArrayList<>();
        seenBullet = false;
      }
      current.add(i);
      seenBullet = seenBullet || bullet;
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  private TemplateMetadata buildTemplate(
      XWPFDocument document,
      List<ParagraphView> paragraphs,
      List<TemplateMetadata.SectionAnchor> anchors,
      tools.jackson.databind.JsonNode mappings,
      String sourceChecksum) {
    CTSectPr sectPr = document.getDocument().getBody().getSectPr();
    double width = 595;
    double height = 842;
    double top = 72;
    double bottom = 72;
    double left = 72;
    double right = 72;
    if (sectPr != null) {
      CTPageSz pageSz = sectPr.getPgSz();
      if (pageSz != null) {
        width = toPoints(pageSz.getW());
        height = toPoints(pageSz.getH());
      }
      CTPageMar pageMar = sectPr.getPgMar();
      if (pageMar != null) {
        top = toPoints(pageMar.getTop());
        bottom = toPoints(pageMar.getBottom());
        left = toPoints(pageMar.getLeft());
        right = toPoints(pageMar.getRight());
      }
    }
    ObjectNode properties = objectMapper.createObjectNode();
    ArrayNode paragraphNodes = objectMapper.createArrayNode();    for (ParagraphView view : paragraphs) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("id", view.id());
      node.put("text", view.text());
      if (view.style() != null) {
        node.put("style", view.style());
      }
      if (view.alignment() != null) {
        node.put("alignment", view.alignment());
      }
      if (view.spacing() != null) {
        node.put("spacing", view.spacing());
      }
      if (view.run() != null) {
        node.put("run", view.run());
      }
      node.put("numbered", view.numbered());
      paragraphNodes.add(node);
    }
    properties.set("paragraphs", paragraphNodes);
    properties.put("tables", document.getTables().size());
    properties.put("columns", columnCount(document));
    properties.put("images", imageCount(document));
    properties.put("hasHeader", !document.getHeaderList().isEmpty());
    properties.put("hasFooter", !document.getFooterList().isEmpty());
    properties.put("paragraphCount", paragraphs.size());
    return new TemplateMetadata(DocumentType.DOCX, sourceChecksum,
        new TemplateMetadata.PageGeometry(width, height, top, bottom, left, right),
        anchors, mappings, properties);
  }

  private static double toPoints(Object twips) {
    if (twips instanceof BigInteger value) {
      return value.doubleValue() / 20.0;
    }
    return 0;
  }

  private static int columnCount(XWPFDocument document) {
    try {
      CTSectPr sectPr = document.getDocument().getBody().getSectPr();
      if (sectPr != null && sectPr.getCols() != null && sectPr.getCols().getNum() != null) {
        return Math.max(1, sectPr.getCols().getNum().intValue());
      }
    } catch (Exception e) {
      log.debug("Cannot read column count", e);
    }
    return 1;
  }

  private static int imageCount(XWPFDocument document) {
    try {
      String xml = document.getDocument().xmlText();
      int count = 0;
      int index = 0;
      while ((index = xml.indexOf("<w:drawing", index)) != -1) {
        count++;
        index += 10;
      }
      return count;
    } catch (Exception e) {
      log.debug("Cannot count images", e);
      return 0;
    }
  }

  /**
   * Splits paragraphs into blocks. A block is either a preamble chunk or one
   * heading line plus its body lines; {@code title} holds the heading line
   * only, {@code lines} the body lines only.
   */
  private List<Block> splitBlocks(List<ParagraphView> paragraphs) {
    List<Block> blocks = new ArrayList<>();
    String title = null;
    List<String> lines = new ArrayList<>();
    List<String> nodeIds = new ArrayList<>();
    boolean headingBlock = false;
    boolean seenKnownSection = false;
    boolean seenAnyContent = false;
    for (ParagraphView view : paragraphs) {
      if (view.text().isBlank()) {
        if (!lines.isEmpty() || headingBlock) {
          blocks.add(new Block(title, List.copyOf(lines), List.copyOf(nodeIds), headingBlock));
          title = null;
          lines = new ArrayList<>();
          nodeIds = new ArrayList<>();
          headingBlock = false;
        }
        continue;
      }
      boolean styled = isHeadingStyle(view);
      // A keyword only marks a heading on short, title-shaped lines, so body
      // sentences mentioning e.g. "experience" never become sections. ALL-CAPS
      // titles additionally wait for the first real section (contact blocks
      // stay in the preamble).
      boolean keyword =
          SectionClassifier.classifyHeading(view.text()).isPresent();
      boolean shaped = SectionClassifier.looksLikeHeading(view.text());
      boolean heading = styled || (shaped && (keyword || seenKnownSection));
      if (heading && (seenAnyContent || title != null || !lines.isEmpty())) {
        if (title != null || !lines.isEmpty()) {
          blocks.add(new Block(title, List.copyOf(lines), List.copyOf(nodeIds), headingBlock));
          lines = new ArrayList<>();
          nodeIds = new ArrayList<>();
        }
        title = view.text();
        nodeIds.add(view.id());
        headingBlock = true;
        seenAnyContent = true;
        if (styled || keyword) {
          seenKnownSection = true;
        }
      } else {
        lines.add(view.text());
        nodeIds.add(view.id());
        seenAnyContent = true;
      }
    }
    if (title != null || !lines.isEmpty()) {
      blocks.add(new Block(title, List.copyOf(lines), List.copyOf(nodeIds), headingBlock));
    }
    return blocks;
  }

  private boolean isHeadingStyle(ParagraphView view) {
    return view.style() != null && view.style().toLowerCase().startsWith("heading");
  }

  private int firstHeadingIndex(List<Block> blocks) {
    for (int i = 0; i < blocks.size(); i++) {
      if (blocks.get(i).heading()) {
        return i;
      }
    }
    return blocks.size();
  }

  private static String styleOf(XWPFParagraph paragraph) {
    try {
      return paragraph.getStyle();
    } catch (Exception e) {
      return null;
    }
  }

  private static String alignmentOf(XWPFParagraph paragraph) {
    try {
      return paragraph.getAlignment() == null ? null : paragraph.getAlignment().name();
    } catch (Exception e) {
      return null;
    }
  }

  private static String spacingOf(XWPFParagraph paragraph) {
    try {
      int before = paragraph.getSpacingBefore();
      int after = paragraph.getSpacingAfter();
      if (before == -1 && after == -1) {
        return null;
      }
      return "before=" + before + ",after=" + after;
    } catch (Exception e) {
      return null;
    }
  }

  private static String runStyleOf(XWPFParagraph paragraph) {
    try {
      if (paragraph.getRuns().isEmpty()) {
        return null;
      }
      XWPFRun run = paragraph.getRuns().get(0);
      return "bold=" + run.isBold() + ",size=" + run.getFontSizeAsDouble()
          + ",color=" + run.getColor() + ",font=" + run.getFontFamily();
    } catch (Exception e) {
      return null;
    }
  }

  private record ParagraphView(
      String id, String text, String style, String alignment, String spacing, String run,
      boolean numbered) {
  }

  private record Block(String title, List<String> lines, List<String> nodeIds, boolean heading) {
  }

  private record SectionBody(List<String> lines, List<String> nodeIds) {
  }
}

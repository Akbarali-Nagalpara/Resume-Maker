package com.resumeflow.resume.parser;

import com.resumeflow.exception.ResumeParseException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    Map<String, Integer> keyCounts = new LinkedHashMap<>();
    int position = 0;
    for (int i = firstHeading; i < blocks.size(); i++) {
      Block block = blocks.get(i);
      if (!block.heading()) {
        continue;
      }
      String base = SectionClassifier.classifyHeading(block.title()).orElseGet(
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
      if (isSkillsLabel(block.title()) && "skills".equals(lastSectionKey(sections))) {
        // Skill subcategory label ("Languages:", "Frontend:") stays inside
        // the skills section so values are never orphaned into splinters.
        SectionBody skillsBody = sections.get("skills");
        skillsBody.lines().add(block.title());
        skillsBody.lines().addAll(lines);
        skillsBody.nodeIds().add(block.nodeIds().get(0));
        skillsBody.nodeIds().addAll(contentNodeIds);
        mappings.put("skills", String.join(",", skillsBody.nodeIds()));
        continue;
      }
      if (sections.containsKey(base) && KNOWN_SECTION_KEYS.contains(base)) {
        // A repeated known section (e.g. two "Experience" blocks) merges into
        // the first: all content is preserved under one deterministic key.
        SectionBody existing = sections.get(base);
        existing.lines().addAll(lines);
        existing.nodeIds().addAll(contentNodeIds);
        mappings.put(base, String.join(",", existing.nodeIds()));
        continue;
      }
      int count = keyCounts.merge(base, 1, Integer::sum);
      String key = count == 1 ? base : base + "-" + count;
      while (sections.containsKey(key)) {
        count++;
        key = base + "-" + count;
      }
      keyCounts.put(base, count);
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

  private static boolean isSkillsLabel(String title) {
    if (title == null || !title.endsWith(":") || title.length() >= 30) {
      return false;
    }
    Optional<String> key = SectionClassifier.classifyHeading(title);
    return key.isEmpty() || key.get().equals("skills") || key.get().equals("languages");
  }

  private static String lastSectionKey(Map<String, SectionBody> sections) {
    String last = null;
    for (String key : sections.keySet()) {
      last = key;
    }
    return last;
  }

  private PersonalInfo parsePersonal(List<String> lines) {
    String name = lines.isEmpty() ? "Unnamed" : lines.get(0);
    String title = null;
    String email = null;
    String phone = null;
    String location = null;
    String website = null;
    String github = null;
    String linkedin = null;
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      SectionClassifier.ContactParts parts = SectionClassifier.parseContactLine(line);
      if (line.contains("@") || parts.phone() != null || parts.github() != null
          || parts.linkedin() != null || parts.website() != null) {
        if (parts.email() != null) {
          email = parts.email();
        }
        if (parts.phone() != null) {
          phone = parts.phone();
        }
        if (parts.github() != null) {
          github = parts.github();
        }
        if (parts.linkedin() != null) {
          linkedin = parts.linkedin();
        }
        if (parts.website() != null) {
          website = parts.website();
        }
        if (parts.location() != null) {
          location = location == null ? parts.location() : location + ", " + parts.location();
        }
      } else if (title == null) {
        title = line;
      } else if (location == null) {
        location = line;
      }
    }
    return new PersonalInfo(name, title, email, phone, location, website, github, linkedin);
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
      String stripped = line.strip();
      // Subcategory labels stay verbatim; value lines split on separators.
      if (stripped.endsWith(":") && stripped.length() < 30) {
        if (!skills.contains(stripped)) {
          skills.add(stripped);
        }
      } else {
        for (String skill : SectionClassifier.splitSkills(line)) {
          if (!skills.contains(skill)) {
            skills.add(skill);
          }
        }
      }
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
    ResumeContentModel content = new ResumeContentModel(personal,
        String.join("\n", summary.lines()),
        skills.stream().distinct().toList(), experience, projects, education, additional);
    return sanitize(content);
  }

  /**
   * Incorrect data is worse than missing data: clears fields holding
   * obviously unrelated content. Semantic misassignment is fixed at the
   * section-assignment layer, not here.
   */
  static ResumeContentModel sanitize(ResumeContentModel content) {
    PersonalInfo personal = content.personal();
    String github = personal.github() != null
        && personal.github().toLowerCase().contains("github.com") ? personal.github() : null;
    String linkedin = personal.linkedin() != null
        && personal.linkedin().toLowerCase().contains("linkedin.com") ? personal.linkedin() : null;
    String website = personal.website() != null
        && SectionClassifier.looksLikeUrl(personal.website()) ? personal.website() : null;
    String location = SectionClassifier.normalizeLocation(personal.location());
    PersonalInfo cleanPersonal = new PersonalInfo(personal.name(), personal.title(),
        personal.email(), personal.phone(), location, website, github, linkedin);
    List<EducationItem> education = content.education().stream()
        .map(item -> new EducationItem(item.id(),
            looksLikeUrlOrContact(item.degree()) ? "" : item.degree(),
            looksLikeUrlOrContact(item.school()) ? null : item.school(),
            item.dates()))
        .toList();
    return new ResumeContentModel(cleanPersonal, content.summary(),
        content.skills().stream().filter(skill -> !skill.isBlank()).toList(),
        content.experience(), content.projects(), education, content.additionalSections());
  }

  private static boolean looksLikeUrlOrContact(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    return SectionClassifier.looksLikeUrl(value) || value.contains("@");
  }

  /**
   * Parses experience items, recording each item's paragraph range so the
   * generator rewrites items in place. Lines and node ids are
   * paragraph-aligned.
   */
  static List<ExperienceItem> parseExperience(SectionBody body, ObjectNode mappings) {
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

  static ExperienceItem toExperience(List<String> group, int counter) {
    String heading = group.get(0);
    String role = heading;
    String company = null;
    // Only an explicit " @ " joins role and company on one line; em-dash
    // suffixes belong to the title and must not be split off.
    if (heading.contains(" @ ")) {
      String[] split = heading.split("\\s+@\\s+", 2);
      role = split[0].trim();
      company = split[1].trim();
    }
    String dates = null;
    String location = null;
    List<String> bullets = new ArrayList<>();
    for (int i = 1; i < group.size(); i++) {
      String line = group.get(i).trim();
      if (line.isEmpty()) {
        continue;
      }
      if (dates == null && SectionClassifier.containsDateRange(line) && line.length() < 60) {
        dates = line;
      } else if (SectionClassifier.isBullet(line)) {
        bullets.add(SectionClassifier.stripBullet(line));
      } else if (SectionClassifier.isContactLine(line)) {
        continue;
      } else if ((line.contains("•") || line.contains("|") || line.contains("·"))
          && company == null) {
        String[] parts = SectionClassifier.splitCompanyLocation(line);
        company = parts[0];
        location = parts[1];
      } else if (company == null) {
        company = line;
      } else if (location == null && line.contains(",") && line.length() < 50) {
        location = line;
      } else {
        bullets.add(line);
      }
    }
    if (company != null && SectionClassifier.containsDateRange(company)) {
      dates = company;
      company = null;
    }
    return new ExperienceItem("exp-" + (counter + 1), role, company, location, dates, bullets);
  }

  static List<ProjectItem> parseProjects(SectionBody body, ObjectNode mappings) {
    List<ProjectItem> items = new ArrayList<>();
    int counter = 0;
    for (List<Integer> group : groupBlockIndexes(body.lines())) {
      List<String> groupLines = group.stream().map(body.lines()::get).toList();
      if (groupLines.isEmpty()) {
        continue;
      }
      String name = groupLines.get(0);
      List<String> rest = groupLines.subList(1, groupLines.size());
      String stack = splitProjectStack(rest);
      List<String> description = new ArrayList<>();
      java.util.Set<Integer> stackIndexes = stackIndexes(rest);
      for (int i = 0; i < rest.size(); i++) {
        if (stackIndexes.contains(i)) {
          continue;
        }
        String line = rest.get(i);
        description.add(SectionClassifier.isBullet(line)
            ? SectionClassifier.stripBullet(line)
            : line);
      }
      mappings.put("projects[" + counter + "]",
          String.join(",", group.stream().map(body.nodeIds()::get).toList()));
      items.add(new ProjectItem("project-" + (++counter), name,
          description.isEmpty() ? null : String.join(" ", description), stack));
    }
    return items;
  }

  /**
   * Stack = first run of 3+ stack-token lines anywhere after the name (chip
   * layouts), else the legacy trailing separator line.
   */
  static String splitProjectStack(List<String> rest) {
    List<Integer> run = new ArrayList<>();
    for (int i = 0; i < rest.size(); i++) {
      if (SectionClassifier.isStackToken(rest.get(i))) {
        run.add(i);
      } else {
        if (run.size() >= 3) {
          return joinStack(rest, run);
        }
        run = new ArrayList<>();
      }
    }
    if (run.size() >= 3) {
      return joinStack(rest, run);
    }
    if (!rest.isEmpty()) {
      String last = rest.get(rest.size() - 1);
      if (last.length() < 120 && last.matches("(?i).*[·|/,].*")) {
        return last;
      }
    }
    return null;
  }

  private static String joinStack(List<String> rest, List<Integer> run) {
    StringBuilder joined = new StringBuilder();
    for (int index : run) {
      if (!joined.isEmpty()) {
        joined.append(" · ");
      }
      joined.append(rest.get(index).trim());
    }
    return joined.toString();
  }

  private static java.util.Set<Integer> stackIndexes(List<String> rest) {
    List<Integer> run = new ArrayList<>();
    for (int i = 0; i < rest.size(); i++) {
      if (SectionClassifier.isStackToken(rest.get(i))) {
        run.add(i);
      } else {
        if (run.size() >= 3) {
          return new java.util.HashSet<>(run);
        }
        run = new ArrayList<>();
      }
    }
    if (run.size() >= 3) {
      return new java.util.HashSet<>(run);
    }
    if (!rest.isEmpty()) {
      String last = rest.get(rest.size() - 1);
      if (last.length() < 120 && last.matches("(?i).*[·|/,].*")) {
        return java.util.Set.of(rest.size() - 1);
      }
    }
    return java.util.Set.of();
  }

  static List<EducationItem> parseEducation(SectionBody body, ObjectNode mappings) {
    List<EducationItem> items = new ArrayList<>();
    int counter = 0;
    for (List<Integer> group : groupEducationIndexes(body.lines())) {
      List<String> groupLines = group.stream().map(body.lines()::get).toList();
      if (groupLines.isEmpty()) {
        continue;
      }
      String degree = groupLines.get(0);
      String school = null;
      String dates = null;
      for (int i = 1; i < groupLines.size(); i++) {
        String line = groupLines.get(i);
        if (SectionClassifier.containsDateRange(line)) {
          dates = line;
        } else if (school == null) {
          school = line;
        }
      }
      mappings.put("education[" + counter + "]",
          String.join(",", group.stream().map(body.nodeIds()::get).toList()));
      items.add(new EducationItem("education-" + (++counter), degree, school, dates));
    }
    return items;
  }

  /**
   * One education item per degree: recorded paragraph breaks split
   * deterministically, otherwise a new item starts after a dates line when
   * more content follows (degrees are date-terminated blocks).
   */
  static List<List<Integer>> groupEducationIndexes(List<String> lines) {
    boolean hasBreaks = lines.stream().anyMatch(String::isBlank);
    if (hasBreaks) {
      return splitOnBlanks(lines);
    }
    List<List<Integer>> groups = new ArrayList<>();
    List<Integer> current = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isBlank()) {
        continue;
      }
      if (!current.isEmpty()
          && SectionClassifier.containsDateRange(lines.get(current.get(current.size() - 1)))) {
        groups.add(current);
        current = new ArrayList<>();
      }
      current.add(i);
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  static List<List<Integer>> splitOnBlanks(List<String> lines) {
    List<List<Integer>> groups = new ArrayList<>();
    List<Integer> current = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).isBlank()) {
        if (!current.isEmpty()) {
          groups.add(current);
          current = new ArrayList<>();
        }
        continue;
      }
      current.add(i);
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  /**
   * Splits section lines into item groups. Recorded paragraph breaks split
   * deterministically; otherwise a new item starts only at a title-like line
   * (short, uppercase start, no trailing period) following bullets, so
   * description fragments never split items.
   */
  static List<List<Integer>> groupBlockIndexes(List<String> lines) {
    if (lines.stream().anyMatch(String::isBlank)) {
      return splitOnBlanks(lines);
    }
    List<List<Integer>> groups = new ArrayList<>();
    List<Integer> current = new ArrayList<>();
    boolean seenBullet = false;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      boolean bullet = SectionClassifier.isBullet(line);
      String stripped = line.strip();
      boolean startsItem = !bullet && seenBullet && !current.isEmpty()
          && stripped.length() < 80
          && !stripped.endsWith(".") && !stripped.endsWith(",")
          && !stripped.endsWith(";") && !stripped.endsWith(":")
          && !stripped.isEmpty() && Character.isUpperCase(stripped.charAt(0))
          && !SectionClassifier.continues(current.isEmpty() ? null
              : lines.get(current.get(current.size() - 1)));
      if (startsItem) {
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
      // Authoritative heading test: bullets are never headings; unstyled
      // keyword lines must be short and title-shaped.
      boolean heading = SectionClassifier.isSectionHeading(
          view.text(), styled, seenKnownSection);
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
        seenKnownSection = true;
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

  /** Shared section body CARRIER also used by the PDF fallback parser. */
  record SectionBody(List<String> lines, List<String> nodeIds) {
  }

  /** Section keys with fixed model fields; repeats merge instead of colliding. */
  private static final java.util.Set<String> KNOWN_SECTION_KEYS =
      java.util.Set.of("summary", "skills", "experience", "projects", "education");
}

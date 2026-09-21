package com.resumeflow.resume.parser;

import com.resumeflow.exception.ResumeParseException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * PDF parser built on Apache PDFBox. Extracts editable content with page/line
 * source mappings and captures page geometry, fonts and text-block coordinates
 * as template metadata.
 *
 * <p>Known limitation: arbitrary PDF round-tripping cannot be pixel-perfect.
 * Generation re-renders content using the captured geometry instead of editing
 * the original content stream in place.
 */
@Component
public class PdfResumeParser implements ResumeParser {

  private static final Logger log = LoggerFactory.getLogger(PdfResumeParser.class);
  private static final int MAX_TEMPLATE_BLOCKS = 400;

  private final ObjectMapper objectMapper;

  public PdfResumeParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.PDF;
  }

  @Override
  public ParsedResume parse(Path file, String sourceChecksum) {
    try (PDDocument document = Loader.loadPDF(file.toFile())) {
      PositionCapture capture = new PositionCapture();
      capture.setSortByPosition(true);
      String text = capture.getText(document);
      List<String> lines = text.lines().toList();
      return assemble(lines, capture.blocks, document, sourceChecksum);
    } catch (ResumeParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ResumeParseException("Cannot parse PDF resume", e);
    }
  }

  private ParsedResume assemble(
      List<String> lines, List<TextBlock> blocks, PDDocument document, String sourceChecksum) {
    List<Block> docBlocks = splitBlocks(lines);
    int firstHeading = firstHeadingIndex(docBlocks);
    List<String> preamble = new ArrayList<>();
    for (Block block : docBlocks.subList(0, firstHeading)) {
      preamble.addAll(block.lines());
    }
    PersonalInfo personal = parsePersonal(preamble);

    Map<String, SectionBody> sections = new LinkedHashMap<>();
    Map<String, String> titles = new LinkedHashMap<>();
    List<ParsedResume.SectionIndexEntry> index = new ArrayList<>();
    List<TemplateMetadata.SectionAnchor> anchors = new ArrayList<>();
    ObjectNode mappings = objectMapper.createObjectNode();
    int position = 0;
    for (int i = firstHeading; i < docBlocks.size(); i++) {
      Block block = docBlocks.get(i);
      if (!block.heading()) {
        continue;
      }
      String key = SectionClassifier.classifyHeading(block.title()).orElseGet(
          () -> "custom-" + block.title().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
      List<String> sectionLines = new ArrayList<>(block.lines());
      List<String> nodeIds = new ArrayList<>(block.nodeIds());
      for (int j = i + 1; j < docBlocks.size() && !docBlocks.get(j).heading(); j++) {
        sectionLines.addAll(docBlocks.get(j).lines());
        nodeIds.addAll(docBlocks.get(j).nodeIds());
      }
      sections.put(key, new SectionBody(sectionLines, nodeIds));
      titles.put(key, block.title());
      String nodeRef = String.join(",", nodeIds);
      mappings.put(key, nodeRef);
      index.add(new ParsedResume.SectionIndexEntry(key, block.title(), position));
      anchors.add(new TemplateMetadata.SectionAnchor(key, block.title(), position, nodeRef));
      position++;
    }

    ResumeContentModel content = buildContent(personal, sections, titles, mappings);
    TemplateMetadata template = buildTemplate(document, blocks, anchors, mappings, sourceChecksum);
    return new ParsedResume(DocumentType.PDF, content, template, index);
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
    for (String line : sections.getOrDefault("skills", new SectionBody(List.of(), List.of()))
        .lines()) {
      skills.addAll(SectionClassifier.splitSkills(line));
    }
    List<ExperienceItem> experience = new ArrayList<>();
    List<List<String>> expGroups = DocxResumeParser.groupExperienceLines(
        sections.getOrDefault("experience", new SectionBody(List.of(), List.of())).lines());
    for (int i = 0; i < expGroups.size(); i++) {
      experience.add(toExperience(expGroups.get(i), i));
    }
    List<ProjectItem> projects = new ArrayList<>();
    int projCounter = 0;
    for (List<String> group : groupBlocks(
        sections.getOrDefault("projects", new SectionBody(List.of(), List.of())).lines())) {
      if (!group.isEmpty()) {
        projects.add(toProject(group, projCounter++));
      }
    }
    List<EducationItem> education = new ArrayList<>();
    List<String> eduLines =
        sections.getOrDefault("education", new SectionBody(List.of(), List.of())).lines();
    if (!eduLines.isEmpty()) {
      education.add(new EducationItem("education-1", eduLines.get(0),
          eduLines.size() > 1 ? eduLines.get(1) : null,
          eduLines.size() > 2 ? eduLines.get(eduLines.size() - 1) : null));
    }
    List<ResumeContentModel.AdditionalSection> additional = new ArrayList<>();
    sections.forEach((key, body) -> {
      if (!List.of("summary", "skills", "experience", "projects", "education").contains(key)) {
        additional.add(new ResumeContentModel.AdditionalSection(key,
            titles.getOrDefault(key, key), body.lines()));
      }
    });
    mappings.put("personal.name", "l1");
    return new ResumeContentModel(personal, String.join("\n", summary.lines()),
        skills.stream().distinct().toList(), experience, projects, education, additional);
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
    return new ExperienceItem("exp-" + (counter + 1), role, company, null, dates, bullets);
  }

  private ProjectItem toProject(List<String> group, int counter) {
    String name = group.get(0);
    String stack = null;
    List<String> description = new ArrayList<>();
    for (int i = 1; i < group.size(); i++) {
      String line = SectionClassifier.isBullet(group.get(i))
          ? SectionClassifier.stripBullet(group.get(i))
          : group.get(i);
      if (i == group.size() - 1 && line.matches("(?i).*[·|/,].*") && line.length() < 120) {
        stack = line;
      } else {
        description.add(line);
      }
    }
    return new ProjectItem("project-" + (counter + 1), name, String.join(" ", description), stack);
  }

  private static List<List<String>> groupBlocks(List<String> lines) {
    List<List<String>> groups = new ArrayList<>();
    List<String> current = new ArrayList<>();
    boolean seenBullet = false;
    for (String line : lines) {
      boolean bullet = SectionClassifier.isBullet(line);
      if (!bullet && seenBullet && !current.isEmpty()) {
        groups.add(current);
        current = new ArrayList<>();
        seenBullet = false;
      }
      current.add(line);
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  private TemplateMetadata buildTemplate(
      PDDocument document,
      List<TextBlock> blocks,
      List<TemplateMetadata.SectionAnchor> anchors,
      JsonNode mappings,
      String sourceChecksum) {
    ObjectNode properties = objectMapper.createObjectNode();
    ArrayNode pages = objectMapper.createArrayNode();
    double width = 595;
    double height = 842;
    int pageCount = document.getNumberOfPages();
    for (int i = 0; i < pageCount; i++) {
      PDPage page = document.getPage(i);
      PDRectangle box = page.getMediaBox();
      ObjectNode pageNode = objectMapper.createObjectNode();
      pageNode.put("number", i + 1);
      pageNode.put("width", box.getWidth());
      pageNode.put("height", box.getHeight());
      pages.add(pageNode);
      if (i == 0) {
        width = box.getWidth();
        height = box.getHeight();
      }
    }
    properties.set("pages", pages);
    properties.put("pageCount", pageCount);
    Set<String> fonts = new LinkedHashSet<>();
    ArrayNode blockNodes = objectMapper.createArrayNode();
    for (TextBlock block : blocks.stream().limit(MAX_TEMPLATE_BLOCKS).toList()) {
      fonts.add(block.font() + "@" + block.fontSize());
      ObjectNode node = objectMapper.createObjectNode();
      node.put("id", block.id());
      node.put("page", block.page());
      node.put("x", block.x());
      node.put("y", block.y());
      node.put("width", block.width());
      node.put("height", block.height());
      node.put("font", block.font());
      node.put("fontSize", block.fontSize());
      node.put("text", block.text());
      blockNodes.add(node);
    }
    ArrayNode fontNodes = objectMapper.createArrayNode();
    fonts.forEach(fontNodes::add);
    properties.set("fonts", fontNodes);
    properties.set("blocks", blockNodes);
    properties.put("sampledBlocks", Math.min(blocks.size(), MAX_TEMPLATE_BLOCKS));
    return new TemplateMetadata(DocumentType.PDF, sourceChecksum,
        new TemplateMetadata.PageGeometry(width, height, 72, 72, 72, 72),
        anchors, mappings, properties);
  }

  private List<Block> splitBlocks(List<String> rawLines) {
    List<Block> blocks = new ArrayList<>();
    String title = null;
    List<String> lines = new ArrayList<>();
    List<String> nodeIds = new ArrayList<>();
    boolean headingBlock = false;
    boolean seenKnownSection = false;
    boolean seenAnyContent = false;
    int lineNumber = 0;
    for (String raw : rawLines) {
      String line = raw == null ? "" : raw.trim();
      lineNumber++;
      if (line.isBlank()) {
        if (!lines.isEmpty() || headingBlock) {
          blocks.add(new Block(title, List.copyOf(lines), List.copyOf(nodeIds), headingBlock));
          title = null;
          lines = new ArrayList<>();
          nodeIds = new ArrayList<>();
          headingBlock = false;
        }
        continue;
      }
      boolean keyword = SectionClassifier.classifyHeading(line).isPresent();
      boolean shaped = SectionClassifier.looksLikeHeading(line);
      boolean heading = shaped && (keyword || seenKnownSection);
      if (heading && (seenAnyContent || title != null || !lines.isEmpty())) {
        if (title != null || !lines.isEmpty()) {
          blocks.add(new Block(title, List.copyOf(lines), List.copyOf(nodeIds), headingBlock));
          lines = new ArrayList<>();
          nodeIds = new ArrayList<>();
        }
        title = line;
        nodeIds.add("l" + lineNumber);
        headingBlock = true;
        seenAnyContent = true;
        if (keyword) {
          seenKnownSection = true;
        }
      } else {
        lines.add(line);
        nodeIds.add("l" + lineNumber);
        seenAnyContent = true;
      }
    }
    if (title != null || !lines.isEmpty()) {
      blocks.add(new Block(title, List.copyOf(lines), List.copyOf(nodeIds), headingBlock));
    }
    return blocks;
  }

  private int firstHeadingIndex(List<Block> blocks) {
    for (int i = 0; i < blocks.size(); i++) {
      if (blocks.get(i).heading()) {
        return i;
      }
    }
    return blocks.size();
  }

  private record Block(String title, List<String> lines, List<String> nodeIds, boolean heading) {
  }

  private record SectionBody(List<String> lines, List<String> nodeIds) {
  }

  private record TextBlock(
      String id, int page, float x, float y, float width, float height, String font,
      float fontSize, String text) {
  }

  private static class PositionCapture extends PDFTextStripper {

    private final List<TextBlock> blocks = new ArrayList<>();
    private int counter;

    PositionCapture() throws IOException {
      super();
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
      super.writeString(text, textPositions);
      if (text == null || text.isBlank() || textPositions.isEmpty()) {
        return;
      }
      TextPosition first = textPositions.get(0);
      TextPosition last = textPositions.get(textPositions.size() - 1);
      float x = first.getXDirAdj();
      float y = first.getYDirAdj();
      float width = (last.getXDirAdj() + last.getWidthDirAdj()) - x;
      blocks.add(new TextBlock("t" + (counter++), getCurrentPageNo(), x, y,
          Math.max(width, 0), first.getHeightDir(),
          first.getFont().getName(), first.getFontSizeInPt(), text.trim()));
    }
  }
}

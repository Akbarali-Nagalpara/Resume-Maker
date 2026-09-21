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
      // Trigger positioning; line order below comes from captured blocks,
      // not the stripper text, so columns never interleave on shared baselines.
      capture.getText(document);
      List<String> lines = readingOrderLines(capture.blocks, document);
      return assemble(lines, capture.blocks, document, sourceChecksum);
    } catch (ResumeParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ResumeParseException("Cannot parse PDF resume", e);
    }
  }

  /**
   * Column-aware reading order: blocks are ordered by (page, column, y) and
   * blank separators are reinserted on large vertical gaps, so two-column
   * layouts never merge across columns the way raw stripper lines do.
   */
  static List<String> readingOrderLines(List<TextBlock> blocks, PDDocument document) {
    Map<Integer, List<TextBlock>> byPage = new LinkedHashMap<>();
    for (TextBlock block : blocks) {
      byPage.computeIfAbsent(block.page(), key -> new ArrayList<>()).add(block);
    }
    List<String> lines = new ArrayList<>();
    for (Map.Entry<Integer, List<TextBlock>> entry : byPage.entrySet().stream()
        .sorted(Map.Entry.comparingByKey()).toList()) {
      List<TextBlock> pageBlocks = entry.getValue();
      assignPageColumns(pageBlocks, document);
      pageBlocks.sort(java.util.Comparator
          .comparingInt(TextBlock::column)
          .thenComparingDouble(TextBlock::y));
      TextBlock previous = null;
      for (TextBlock block : pageBlocks) {
        if (previous != null && previous.column() == block.column()
            && block.y() - (previous.y() + previous.height()) > gapThreshold(previous)) {
          lines.add("");
        }
        lines.add(block.text());
        previous = block;
      }
      lines.add("");
    }
    return lines;
  }

  private static double gapThreshold(TextBlock previous) {
    return Math.max(12.0, previous.height() * 1.8);
  }

  private static void assignPageColumns(List<TextBlock> pageBlocks, PDDocument document) {
    int columns = detectColumns(pageBlocks);
    float pageWidth = 595;
    try {
      if (!pageBlocks.isEmpty()) {
        int pageIndex = Math.max(0, pageBlocks.get(0).page() - 1);
        if (pageIndex < document.getNumberOfPages()) {
          pageWidth = document.getPage(pageIndex).getMediaBox().getWidth();
        }
      }
    } catch (Exception e) {
      log.debug("Cannot read page width for columns", e);
    }
    for (int i = 0; i < pageBlocks.size(); i++) {
      TextBlock block = pageBlocks.get(i);
      int column = 0;
      if (!(block.width() > 0.6 * pageWidth) && columns == 2) {
        float center = block.x() + block.width() / 2;
        column = center < pageWidth / 2 ? 0 : 1;
      }
      pageBlocks.set(i, block.withColumn(column));
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
    Map<String, Integer> keyCounts = new LinkedHashMap<>();
    int position = 0;
    for (int i = firstHeading; i < docBlocks.size(); i++) {
      Block block = docBlocks.get(i);
      if (!block.heading()) {
        continue;
      }
      String base = SectionClassifier.classifyHeading(block.title()).orElseGet(
          () -> "custom-" + block.title().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
      List<String> sectionLines = new ArrayList<>(block.lines());
      List<String> nodeIds = new ArrayList<>(block.nodeIds());
      for (int j = i + 1; j < docBlocks.size() && !docBlocks.get(j).heading(); j++) {
        sectionLines.addAll(docBlocks.get(j).lines());
        nodeIds.addAll(docBlocks.get(j).nodeIds());
      }
      if (sections.containsKey(base) && KNOWN_SECTION_KEYS.contains(base)) {
        // Repeated known section merges into the first: no content lost,
        // one deterministic key.
        SectionBody existing = sections.get(base);
        existing.lines().addAll(sectionLines);
        existing.nodeIds().addAll(nodeIds);
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

  private DocxResumeParser.SectionBody toSharedBody(SectionBody body) {
    return new DocxResumeParser.SectionBody(
        new java.util.ArrayList<>(body.lines()), new java.util.ArrayList<>(body.nodeIds()));
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
      String stripped = line.strip();
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
    List<ExperienceItem> experience = DocxResumeParser.parseExperience(
        toSharedBody(sections.getOrDefault("experience",
            new SectionBody(List.of(), List.of()))),
        mappings);
    List<ProjectItem> projects = DocxResumeParser.parseProjects(
        toSharedBody(
            sections.getOrDefault("projects", new SectionBody(List.of(), List.of()))),
        mappings);
    List<EducationItem> education = DocxResumeParser.parseEducation(
        toSharedBody(sections.getOrDefault("education",
            new SectionBody(List.of(), List.of()))),
        mappings);
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
      node.put("column", block.column());
      node.put("text", block.text());
      blockNodes.add(node);
    }
    ArrayNode fontNodes = objectMapper.createArrayNode();
    fonts.forEach(fontNodes::add);
    properties.set("fonts", fontNodes);
    properties.set("blocks", blockNodes);
    properties.put("sampledBlocks", Math.min(blocks.size(), MAX_TEMPLATE_BLOCKS));
    properties.put("columns", detectColumns(blocks));
    properties.put("images", countImages(document));
    properties.put("tables", 0);
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
      boolean heading = SectionClassifier.isSectionHeading(line, false, seenKnownSection);
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
        seenKnownSection = true;
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

  private static int detectColumns(List<TextBlock> blocks) {
    if (blocks.size() < 6) {
      return 1;
    }
    List<Float> xs = blocks.stream().map(TextBlock::x).sorted().toList();
    float bestGap = 0;
    for (int i = 0; i + 1 < xs.size(); i++) {
      bestGap = Math.max(bestGap, xs.get(i + 1) - xs.get(i));
    }
    return bestGap > 100 ? 2 : 1;
  }

  private static int countImages(PDDocument document) {
    int count = 0;
    try {
      for (PDPage page : document.getPages()) {
        if (page.getResources() == null) {
          continue;
        }
        for (var name : page.getResources().getXObjectNames()) {
          if (page.getResources().getXObject(name)
              instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
            count++;
          }
        }
      }
    } catch (Exception e) {
      log.debug("Cannot count PDF images", e);
    }
    return count;
  }

  private int firstHeadingIndex(List<Block> blocks) {    for (int i = 0; i < blocks.size(); i++) {
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

  /** Section keys with fixed model fields; repeats merge instead of colliding. */
  private static final Set<String> KNOWN_SECTION_KEYS =
      Set.of("summary", "skills", "experience", "projects", "education");

  private record TextBlock(
      String id, int page, float x, float y, float width, float height, String font,
      float fontSize, String text, int column) {

    TextBlock withColumn(int column) {
      return new TextBlock(id, page, x, y, width, height, font, fontSize, text, column);
    }
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
          first.getFont().getName(), first.getFontSizeInPt(), text.trim(), 0));
    }
  }
}

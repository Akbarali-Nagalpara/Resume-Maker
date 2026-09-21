package com.resumeflow.resume.generator;

import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.TemplateMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders resume content into a PDF reusing the captured template geometry:
 * page size, margins, section order and base font sizes.
 */
@Component
public class LayoutPdfRenderer {

  private static final float MARGIN_FALLBACK = 72;
  private static final float NAME_SIZE = 22;
  private static final float TITLE_SIZE = 11;
  private static final float HEADING_SIZE = 11;
  private static final float BODY_SIZE = 10;
  private static final float LINE_GAP = 4;
  private static final PDType1Font HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final PDType1Font HELVETICA_BOLD =
      new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
  private static final PDType1Font HELVETICA_OBLIQUE =
      new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

  public void render(
      ResumeContentModel content, TemplateMetadata template, Path outputFile) {
    TemplateMetadata.PageGeometry page = template.page();
    float pageWidth = (float) page.widthPoints();
    float pageHeight = (float) page.heightPoints();
    float marginTop = (float) page.marginTopPoints();
    float marginLeft = (float) page.marginLeftPoints();
    float marginRight = (float) page.marginRightPoints();
    float marginBottom = (float) page.marginBottomPoints();
    if (marginTop <= 0) {
      marginTop = MARGIN_FALLBACK;
    }
    if (marginLeft <= 0) {
      marginLeft = MARGIN_FALLBACK;
    }
    if (marginRight <= 0) {
      marginRight = MARGIN_FALLBACK;
    }
    if (marginBottom <= 0) {
      marginBottom = MARGIN_FALLBACK;
    }
    try (PDDocument document = new PDDocument()) {
      Cursor cursor = new Cursor(document, pageWidth, pageHeight, marginTop, marginLeft,
          marginRight, marginBottom);
      renderHeader(content, cursor);
      renderSectionAnchors(content, template, cursor);
      cursor.close();
      document.save(outputFile.toFile());
    } catch (ResumeParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ResumeParseException("Cannot render PDF resume", e);
    }
  }

  private void renderHeader(ResumeContentModel content, Cursor cursor) throws IOException {
    if (content.personal() != null) {
      if (content.personal().name() != null) {
        cursor.writeLine(content.personal().name(), HELVETICA_BOLD, NAME_SIZE);
      }
      if (content.personal().title() != null) {
        cursor.writeLine(content.personal().title(), HELVETICA_BOLD, TITLE_SIZE);
      }
      List<String> contact = new ArrayList<>();
      if (content.personal().email() != null) {
        contact.add(content.personal().email());
      }
      if (content.personal().phone() != null) {
        contact.add(content.personal().phone());
      }
      if (content.personal().location() != null) {
        contact.add(content.personal().location());
      }
      if (content.personal().website() != null) {
        contact.add(content.personal().website());
      }
      if (!contact.isEmpty()) {
        cursor.writeLine(String.join(" | ", contact), HELVETICA, BODY_SIZE);
      }
      cursor.gap();
    }
  }

  private void renderSectionAnchors(
      ResumeContentModel content, TemplateMetadata template, Cursor cursor) throws IOException {
    for (TemplateMetadata.SectionAnchor anchor : template.sections()) {
      switch (anchor.key()) {
        case "summary" -> {
          if (content.summary() != null && !content.summary().isBlank()) {
            cursor.writeHeading(anchor.title());
            cursor.writeParagraph(content.summary());
          }
        }
        case "skills" -> {
          if (content.skills() != null && !content.skills().isEmpty()) {
            cursor.writeHeading(anchor.title());
            cursor.writeParagraph(String.join(" | ", content.skills()));
          }
        }
        case "experience" -> {
          if (content.experience() != null && !content.experience().isEmpty()) {
            cursor.writeHeading(anchor.title());
            for (var item : content.experience()) {
              String heading = item.role() == null ? "" : item.role();
              if (item.company() != null && !item.company().isBlank()) {
                heading += " @ " + item.company();
              }
              cursor.writeLine(heading, HELVETICA_BOLD, BODY_SIZE);
              if (item.dates() != null) {
                cursor.writeLine(item.dates(), HELVETICA_OBLIQUE, BODY_SIZE);
              }
              if (item.bullets() != null) {
                for (String bullet : item.bullets()) {
                  cursor.writeParagraph("\u2022 " + bullet);
                }
              }
              cursor.gap();
            }
          }
        }
        case "projects" -> {
          if (content.projects() != null && !content.projects().isEmpty()) {
            cursor.writeHeading(anchor.title());
            for (var item : content.projects()) {
              if (item.name() != null) {
                cursor.writeLine(item.name(), HELVETICA_BOLD, BODY_SIZE);
              }
              if (item.description() != null) {
                cursor.writeParagraph(item.description());
              }
              if (item.stack() != null) {
                cursor.writeParagraph(item.stack());
              }
              cursor.gap();
            }
          }
        }
        case "education" -> {
          if (content.education() != null && !content.education().isEmpty()) {
            cursor.writeHeading(anchor.title());
            for (var item : content.education()) {
              if (item.degree() != null) {
                cursor.writeLine(item.degree(), HELVETICA_BOLD, BODY_SIZE);
              }
              if (item.school() != null) {
                cursor.writeLine(item.school(), HELVETICA, BODY_SIZE);
              }
              if (item.dates() != null) {
                cursor.writeLine(item.dates(), HELVETICA, BODY_SIZE);
              }
              cursor.gap();
            }
          }
        }
        default -> {
          if (content.additionalSections() != null) {
            content.additionalSections().stream()
                .filter(section -> section.key().equals(anchor.key()))
                .findFirst()
                .ifPresent(section -> {
                  try {
                    cursor.writeHeading(anchor.title());
                    for (String paragraph : section.paragraphs()) {
                      cursor.writeParagraph(paragraph);
                    }
                  } catch (IOException e) {
                    throw new ResumeParseException("Cannot render PDF section", e);
                  }
                });
          }
        }
      }
    }
  }

  private static class Cursor {

    private final PDDocument document;
    private final float pageWidth;
    private final float pageHeight;
    private final float marginTop;
    private final float marginLeft;
    private final float marginRight;
    private final float marginBottom;
    private PDPageContentStream stream;
    private float y;

    Cursor(
        PDDocument document,
        float pageWidth,
        float pageHeight,
        float marginTop,
        float marginLeft,
        float marginRight,
        float marginBottom) throws IOException {
      this.document = document;
      this.pageWidth = pageWidth;
      this.pageHeight = pageHeight;
      this.marginTop = marginTop;
      this.marginLeft = marginLeft;
      this.marginRight = marginRight;
      this.marginBottom = marginBottom;
      newPage();
    }

    void writeHeading(String text) throws IOException {
      gap();
      writeLine(text.toUpperCase(), HELVETICA_BOLD, HEADING_SIZE);
    }

    void writeParagraph(String text) throws IOException {
      PDType1Font font = HELVETICA;
      for (String line : wrap(text, font, BODY_SIZE)) {
        writeLine(line, font, BODY_SIZE);
      }
    }

    void writeLine(String text, PDType1Font font, float size) throws IOException {
      float height = size + LINE_GAP;
      if (y - height < marginBottom) {
        newPage();
      }
      stream.beginText();
      stream.setFont(font, size);
      stream.newLineAtOffset(marginLeft, y - size);
      stream.showText(sanitize(text));
      stream.endText();
      y -= height;
    }

    void gap() {
      y -= LINE_GAP * 2;
    }

    void close() throws IOException {
      if (stream != null) {
        stream.close();
        stream = null;
      }
    }

    private void newPage() throws IOException {
      close();
      PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      y = pageHeight - marginTop;
    }

    private List<String> wrap(String text, PDType1Font font, float size) throws IOException {
      float maxWidth = pageWidth - marginLeft - marginRight;
      List<String> lines = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      for (String word : text.split("\\s+")) {
        String candidate = current.isEmpty() ? word : current + " " + word;
        float width = font.getStringWidth(sanitize(candidate))
            / 1000 * size;
        if (width > maxWidth && !current.isEmpty()) {
          lines.add(current.toString());
          current = new StringBuilder(word);
        } else {
          current = new StringBuilder(candidate);
        }
      }
      if (!current.isEmpty()) {
        lines.add(current.toString());
      }
      return lines;
    }

    private static String sanitize(String text) {
      StringBuilder clean = new StringBuilder();
      text.codePoints().forEach(codePoint -> {
        if (codePoint >= 32 && codePoint != 127) {
          clean.appendCodePoint(codePoint);
        } else if (codePoint == '\t') {
          clean.append("  ");
        }
      });
      return clean.toString();
    }
  }
}

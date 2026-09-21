package com.resumeflow.resume.parser;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/** Builds representative resume documents for parser/generator tests. */
public final class SampleResumes {

  private SampleResumes() {
  }

  public static Path docxSample(Path directory) throws IOException {
    return docxSample(directory, "sample-resume.docx", false, false, false, true);
  }

  public static Path docxSample(
      Path directory, String filename, boolean twoColumn, boolean multiPage, boolean withTable)
      throws IOException {
    return docxSample(directory, filename, twoColumn, multiPage, withTable, false);
  }

  public static Path docxSample(
      Path directory,
      String filename,
      boolean twoColumn,
      boolean multiPage,
      boolean withTable,
      boolean withHeaderFooter)
      throws IOException {
    XWPFDocument document = new XWPFDocument();
    if (withHeaderFooter) {
      var header = document.createHeader(
          org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
      header.createParagraph().createRun().setText("Maya Chen — Resume");
      var footer = document.createFooter(
          org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
      footer.createParagraph().createRun().setText("Page");
    }
    addParagraph(document, "Maya Chen", true, 22.0);
    addParagraph(document, "Senior Product Engineer", false, 12.0);
    addParagraph(document, "maya.chen@example.com | +1 415 555 0142 | San Francisco, CA", false,
        10.0);
    addHeading(document, "Summary");
    addParagraph(document, "Product-minded engineer with 7+ years of experience.", false, 10.0);
    addHeading(document, "Skills");
    addParagraph(document, "TypeScript | React | Java | Spring Boot", false, 10.0);
    addHeading(document, "Experience");
    addParagraph(document, "Senior Product Engineer @ Northstar Labs", true, 11.0);
    addParagraph(document, "2022 \u2014 Present", false, 10.0);
    addBullet(document, "Led delivery of a workflow platform.");
    addBullet(document, "Reduced page load time by 38%.");
    addParagraph(document, "", false, 10.0);
    addParagraph(document, "Software Engineer @ Fieldwork Studio", true, 11.0);
    addParagraph(document, "2019 \u2014 2022", false, 10.0);
    addBullet(document, "Built reusable commerce tools.");
    addHeading(document, "Projects");
    addParagraph(document, "Open Metrics Kit", true, 11.0);
    addParagraph(document, "A lightweight observability starter.", false, 10.0);
    addParagraph(document, "TypeScript \u00b7 React", false, 10.0);
    addHeading(document, "Education");
    addParagraph(document, "B.S. Computer Science", true, 11.0);
    addParagraph(document, "University of California, Davis", false, 10.0);
    addParagraph(document, "2015 \u2014 2019", false, 10.0);
    if (twoColumn) {
      var sectPr = document.getDocument().getBody().getSectPr();
      var sect = sectPr == null ? document.getDocument().getBody().addNewSectPr() : sectPr;
      var cols = sect.getCols() == null ? sect.addNewCols() : sect.getCols();
      cols.setNum(java.math.BigInteger.valueOf(2));
    }
    if (withTable) {
      var table = document.createTable(2, 2);
      table.getRow(0).getCell(0).setText("Year");
      table.getRow(0).getCell(1).setText("Role");
      table.getRow(1).getCell(0).setText("2022");
      table.getRow(1).getCell(1).setText("Engineer");
    }
    if (multiPage) {
      for (int i = 0; i < 60; i++) {
        addParagraph(document, "Additional background line " + i + ".", false, 10.0);
      }
    }
    Path file = directory.resolve(filename);
    try (OutputStream out = Files.newOutputStream(file)) {
      document.write(out);
    }
    document.close();
    return file;
  }

  public static Path pdfSample(Path directory) throws IOException {
    return pdfSample(directory, "sample-resume.pdf", false, false);
  }

  public static Path pdfSample(Path directory, String filename, boolean twoColumn,
      boolean multiPage) throws IOException {
    PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.LETTER);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        float y = 700;
        y = pdfLine(stream, "Maya Chen", bold, 20, 72, y);
        y = pdfLine(stream, "Senior Product Engineer", bold, 11, 72, y);
        y = pdfLine(stream, "maya.chen@example.com | San Francisco, CA", regular, 10, 72, y);
        y -= 8;
        y = pdfLine(stream, "SUMMARY", bold, 11, 72, y);
        y = pdfLine(stream, "Product-minded engineer with 7+ years of experience.", regular, 10, 72,
            y);
        y -= 8;
        y = pdfLine(stream, "SKILLS", bold, 11, 72, y);
        y = pdfLine(stream, "TypeScript | React | Java | Spring Boot", regular, 10, 72, y);
        y -= 8;
        y = pdfLine(stream, "EXPERIENCE", bold, 11, 72, y);
        y = pdfLine(stream, "Senior Product Engineer @ Northstar Labs", bold, 10, 72, y);
        y = pdfLine(stream, "2022 - Present", regular, 10, 72, y);
        y = pdfLine(stream, "- Led delivery of a workflow platform.", regular, 10, 72, y);
        if (twoColumn) {
          // Right column band: skills repeated far right to force two bands.
          pdfLine(stream, "LANGUAGES", bold, 11, 330, 620);
          pdfLine(stream, "English | Hindi", regular, 10, 330, 606);
          pdfLine(stream, "Java | Go", regular, 10, 330, 592);
        }
        y = pdfLine(stream, "EDUCATION", bold, 11, 72, y);
        y = pdfLine(stream, "B.S. Computer Science", bold, 10, 72, y);
        pdfLine(stream, "University of California, Davis", regular, 10, 72, y);
      }
      if (multiPage) {
        PDPage second = new PDPage(PDRectangle.LETTER);
        document.addPage(second);
        try (PDPageContentStream stream2 = new PDPageContentStream(document, second)) {
          pdfLine(stream2, "References available on request.", regular, 10, 72, 700);
        }
      }
      Path file = directory.resolve(filename);
      document.save(file.toFile());
      return file;
    }
  }

  private static void addParagraph(XWPFDocument document, String text, boolean bold, double size) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(bold);
    run.setFontSize(size);
  }

  private static void addHeading(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setStyle("Heading1");
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
  }

  private static void addBullet(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText("\u2022 " + text);
  }

  private static float pdfLine(
      PDPageContentStream stream, String text, PDType1Font font, float size, float x, float y)
      throws IOException {
    stream.beginText();
    stream.setFont(font, size);
    stream.newLineAtOffset(x, y);
    stream.showText(text);
    stream.endText();
    return y - size - 4;
  }
}

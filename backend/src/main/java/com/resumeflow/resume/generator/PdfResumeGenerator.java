package com.resumeflow.resume.generator;

import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.TemplateMetadata;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * PDF generator. Because arbitrary PDFs cannot be edited in place, generation
 * re-renders the resume into a new PDF that reuses the captured page geometry,
 * section order and base typography from the template metadata. Complex
 * multi-column or heavily designed PDFs will not be pixel-identical; this
 * limitation is explicit and covered by generation quality tests.
 */
@Component
public class PdfResumeGenerator implements ResumeGenerator {

  private final LayoutPdfRenderer renderer;

  public PdfResumeGenerator(LayoutPdfRenderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.PDF;
  }

  @Override
  public GeneratedArtifact generate(
      Path originalFile,
      ResumeContentModel content,
      TemplateMetadata template,
      Path outputFile) {
    // The original file is the geometry source only; bytes are re-rendered.
    renderer.render(content, template, outputFile);
    return ArtifactChecksums.of(outputFile, "application/pdf");
  }
}

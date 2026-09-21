package com.resumeflow.resume.service;

import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.resume.generator.DocxToPdfConverter;
import com.resumeflow.resume.generator.GeneratedArtifact;
import com.resumeflow.resume.generator.ResumeGenerator;
import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.TemplateMetadata;
import com.resumeflow.resume.processor.DocumentProcessorClient;
import com.resumeflow.resume.processor.PdfPatchResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Patches the original document with updated content.
 *
 * <p>Original DOCX + updated content → patched DOCX (styles/layout preserved)
 * → optional PDF conversion. Original PDF + updated content → controlled
 * rebuild reusing the captured layout model. The original file is always an
 * input, never modified in place.
 */
@Service
public class ResumePatchService {

  private final List<ResumeGenerator> generators;
  private final DocxToPdfConverter pdfConverter;
  private final DocumentProcessorClient processorClient;

  public ResumePatchService(
      List<ResumeGenerator> generators,
      DocxToPdfConverter pdfConverter,
      DocumentProcessorClient processorClient) {
    this.generators = generators;
    this.pdfConverter = pdfConverter;
    this.processorClient = processorClient;
  }

  /**
   * Patches {@code originalFile} with {@code content} using the preserved
   * {@code template}, writing the updated document to {@code outputFile}.
   */
  public GeneratedArtifact patch(
      Path originalFile,
      DocumentType type,
      ResumeContentModel content,
      TemplateMetadata template,
      Path outputFile) {
    return generatorFor(type).generate(originalFile, content, template, outputFile);
  }

  /**
   * Surgically patches a PDF: only changed lines are rewritten in place via
   * the document processor. Returns the patched bytes plus explicit warnings
   * for edits that could not be applied without redesigning the layout.
   */
  public PdfPatchResult patchPdf(
      Path originalFile,
      tools.jackson.databind.JsonNode oldContent,
      tools.jackson.databind.JsonNode newContent,
      Path outputFile) {
    String filename = originalFile.getFileName() == null
        ? "resume.pdf"
        : originalFile.getFileName().toString();
    PdfPatchResult result =
        processorClient.patchPdf(originalFile, filename, oldContent, newContent);
    try {
      java.nio.file.Files.write(outputFile, result.pdf());
    } catch (java.io.IOException e) {
      throw new ResumeParseException("Cannot write patched PDF", e);
    }
    return result;
  }

  /**
   * Converts a patched DOCX to PDF via a real renderer when available.
   *
   * @return converted PDF path, or empty when no conversion engine exists
   */
  public Optional<Path> convertDocxToPdf(Path docxFile, Path outputDir) throws Exception {
    return pdfConverter.convert(docxFile, outputDir);
  }

  public boolean pdfConversionAvailable() {
    return pdfConverter.isAvailable();
  }

  private ResumeGenerator generatorFor(DocumentType type) {
    return generators.stream()
        .filter(generator -> generator.supports(type))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No patch generator for " + type));
  }
}

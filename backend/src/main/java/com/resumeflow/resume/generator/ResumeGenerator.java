package com.resumeflow.resume.generator;

import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.TemplateMetadata;
import java.nio.file.Path;

/** Generates an updated document from preserved template plus edited content. */
public interface ResumeGenerator {

  boolean supports(DocumentType type);

  /**
   * Reads {@code originalFile} (never modified in place) and writes the
   * generated document to {@code outputFile}.
   */
  GeneratedArtifact generate(
      Path originalFile,
      ResumeContentModel content,
      TemplateMetadata template,
      Path outputFile);
}

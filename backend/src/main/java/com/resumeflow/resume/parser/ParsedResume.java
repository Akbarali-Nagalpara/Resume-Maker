package com.resumeflow.resume.parser;

import java.util.List;

/** Result of parsing one resume document: editable content plus layout metadata. */
public record ParsedResume(
    DocumentType documentType,
    ResumeContentModel content,
    TemplateMetadata template,
    List<SectionIndexEntry> sections) {

  public record SectionIndexEntry(String key, String title, int position) {
  }
}

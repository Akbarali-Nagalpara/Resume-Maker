package com.resumeflow.resume.parser;

import java.nio.file.Path;

/** Parses one document format into editable content plus template metadata. */
public interface ResumeParser {

  boolean supports(DocumentType type);

  ParsedResume parse(Path file, String sourceChecksum);
}

package com.resumeflow.resume.service;

import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.exception.UnsupportedResumeFormatException;
import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ParsedResume;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.ResumeParser;
import com.resumeflow.resume.parser.TemplateMetadata;
import com.resumeflow.resume.processor.DocumentProcessorClient;
import com.resumeflow.resume.processor.ProcessorResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Selects the format parser and delegates. PDF documents prefer the Python
 * document processor (PyMuPDF layout analysis) with automatic fallback to
 * the embedded PDFBox parser when the processor is disabled or unreachable.
 * No persistence happens here.
 */
@Service
public class ResumeParserService {

  private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);

  private final List<ResumeParser> parsers;
  private final DocumentProcessorClient processorClient;
  private final ObjectMapper objectMapper;

  public ResumeParserService(
      List<ResumeParser> parsers,
      DocumentProcessorClient processorClient,
      ObjectMapper objectMapper) {
    this.parsers = parsers;
    this.processorClient = processorClient;
    this.objectMapper = objectMapper;
  }

  public ParsedResume parse(Path file, DocumentType type, String sourceChecksum) {
    if (type == DocumentType.PDF && processorClient.isEnabled()) {
      try {
        return parseViaProcessor(file, type, sourceChecksum);
      } catch (ResumeParseException e) {
        log.warn("Document processor failed, falling back to embedded PDF parser: {}",
            e.getMessage());
      }
    }
    return parseEmbedded(file, type, sourceChecksum);
  }

  private ParsedResume parseViaProcessor(Path file, DocumentType type, String sourceChecksum) {
    String filename = file.getFileName() == null ? "resume.pdf" : file.getFileName().toString();
    ProcessorResult result = processorClient.processPdf(file, filename, sourceChecksum);
    try {
      ResumeContentModel content =
          objectMapper.treeToValue(result.content(), ResumeContentModel.class);
      TemplateMetadata template =
          objectMapper.treeToValue(result.template(), TemplateMetadata.class);
      List<ParsedResume.SectionIndexEntry> sections = new ArrayList<>();
      JsonNode sectionsNode = result.sections();
      if (sectionsNode != null && sectionsNode.isArray()) {
        for (JsonNode entry : sectionsNode) {
          sections.add(
              new ParsedResume.SectionIndexEntry(
                  entry.path("key").asText(""),
                  entry.path("title").asText(""),
                  entry.path("position").asInt(0)));
        }
      }
      log.info("Parsed PDF via document processor ({} sections)", sections.size());
      return new ParsedResume(type, content, template, sections);
    } catch (IllegalArgumentException e) {
      throw new ResumeParseException("Invalid document processor response", e);
    }
  }

  private ParsedResume parseEmbedded(Path file, DocumentType type, String sourceChecksum) {
    return parsers.stream()
        .filter(parser -> parser.supports(type))
        .findFirst()
        .orElseThrow(() -> new UnsupportedResumeFormatException(type.name()))
        .parse(file, sourceChecksum);
  }
}

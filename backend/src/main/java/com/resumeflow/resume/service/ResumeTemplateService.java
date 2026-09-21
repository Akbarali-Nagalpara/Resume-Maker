package com.resumeflow.resume.service;

import tools.jackson.databind.JsonNode;
import com.resumeflow.exception.ResumeConflictException;
import com.resumeflow.exception.ResumeNotFoundException;
import com.resumeflow.resume.entity.ResumeFileKind;
import com.resumeflow.resume.entity.ResumeTemplate;
import com.resumeflow.resume.repository.ResumeFileRepository;
import com.resumeflow.resume.repository.ResumeRepository;
import com.resumeflow.resume.repository.ResumeTemplateRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns template/layout metadata. Templates are written once at parse time and
 * never modified: this service only reads and validates preservation.
 */
@Service
public class ResumeTemplateService {

  private static final Logger log = LoggerFactory.getLogger(ResumeTemplateService.class);

  private final ResumeTemplateRepository templateRepository;
  private final ResumeFileRepository fileRepository;
  private final ResumeRepository resumeRepository;
  private final ResumeCacheService cacheService;

  public ResumeTemplateService(
      ResumeTemplateRepository templateRepository,
      ResumeFileRepository fileRepository,
      ResumeRepository resumeRepository,
      ResumeCacheService cacheService) {
    this.templateRepository = templateRepository;
    this.fileRepository = fileRepository;
    this.resumeRepository = resumeRepository;
    this.cacheService = cacheService;
  }

  @Transactional(readOnly = true)
  public ResumeTemplate getTemplate(UUID resumeId) {
    resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
    return templateRepository
        .findByResumeId(resumeId)
        .orElseThrow(() -> new IllegalStateException("No template for resume " + resumeId));
  }

  /** Cache-aside read of the template metadata node for editor/preview flows. */
  @Transactional(readOnly = true)
  public JsonNode getTemplateNode(UUID resumeId) {
    return cacheService
        .getTemplate(resumeId)
        .orElseGet(
            () -> {
              JsonNode node = getTemplate(resumeId).getTemplate();
              cacheService.putTemplate(resumeId, node);
              return node;
            });
  }

  /**
   * Verifies the template still describes the stored original document by
   * comparing checksums. Guards against storage tampering or mismatched rows.
   */
  @Transactional(readOnly = true)
  public void validatePreservation(UUID resumeId) {
    ResumeTemplate template = getTemplate(resumeId);
    String originalChecksum =
        fileRepository
            .findFirstByResumeIdAndKindOrderByCreatedAtDesc(resumeId, ResumeFileKind.ORIGINAL)
            .orElseThrow(() -> new IllegalStateException("No original file for " + resumeId))
            .getChecksum();
    if (!template.getSourceChecksum().equals(originalChecksum)) {
      log.error("Template checksum mismatch for resume {}", resumeId);
      throw new ResumeConflictException(
          "Template metadata no longer matches the stored original document.");
    }
  }
}

package com.resumeflow.resume.service;

import tools.jackson.databind.ObjectMapper;
import com.resumeflow.exception.ResumeConflictException;
import com.resumeflow.exception.ResumeNotFoundException;
import com.resumeflow.resume.dto.GenerateResponse;
import com.resumeflow.resume.entity.Resume;
import com.resumeflow.resume.entity.ResumeContent;
import com.resumeflow.resume.entity.ResumeFile;
import com.resumeflow.resume.entity.ResumeFileKind;
import com.resumeflow.resume.entity.ResumeStatus;
import com.resumeflow.resume.entity.ResumeTemplate;
import com.resumeflow.resume.generator.GeneratedArtifact;
import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.parser.TemplateMetadata;
import com.resumeflow.resume.repository.ResumeFileRepository;
import com.resumeflow.resume.repository.ResumeRepository;
import com.resumeflow.resume.storage.FileStorageService;
import com.resumeflow.resume.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Combines the preserved template with the latest edited content into a new
 * immutable artifact. The original file and template metadata are inputs only.
 */
@Service
public class ResumeGenerationService {

  private static final Logger log = LoggerFactory.getLogger(ResumeGenerationService.class);

  private final ResumeRepository resumeRepository;
  private final ResumeFileRepository fileRepository;
  private final ResumeContentService contentService;
  private final ResumeTemplateService templateService;
  private final ResumeVersionService versionService;
  private final FileStorageService storageService;
  private final ResumePatchService patchService;
  private final ObjectMapper objectMapper;

  public ResumeGenerationService(
      ResumeRepository resumeRepository,
      ResumeFileRepository fileRepository,
      ResumeContentService contentService,
      ResumeTemplateService templateService,
      ResumeVersionService versionService,
      FileStorageService storageService,
      ResumePatchService patchService,
      ObjectMapper objectMapper) {
    this.resumeRepository = resumeRepository;
    this.fileRepository = fileRepository;
    this.contentService = contentService;
    this.templateService = templateService;
    this.versionService = versionService;
    this.storageService = storageService;
    this.patchService = patchService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public GenerateResponse generate(java.util.UUID resumeId) {
    Resume resume = resumeRepository.findById(resumeId)
        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
    if (resume.getStatus() != ResumeStatus.READY) {
      throw new ResumeConflictException(
          "Resume cannot be generated while status is " + resume.getStatus());
    }
    templateService.validatePreservation(resumeId);
    ResumeTemplate template = templateService.getTemplate(resumeId);
    ResumeContent content = contentService.latestContent(resumeId);
    DocumentType type = DocumentType.valueOf(template.getDocumentType());
    int nextVersion = versionService.nextVersionNumber(resumeId);

    try {
      ResumeFile original = fileRepository
          .findFirstByResumeIdAndKindOrderByCreatedAtDesc(resumeId, ResumeFileKind.ORIGINAL)
          .orElseThrow(() -> new IllegalStateException("No original file for " + resumeId));
      Path workDir = Files.createTempDirectory("resumeflow-generate-");
      Path originalCopy = workDir.resolve("original." + type.name().toLowerCase());
      Path output = workDir.resolve("generated." + type.name().toLowerCase());
      try {
        Resource originalResource = storageService.load(original.getStorageKey());
        try (InputStream in = originalResource.getInputStream()) {
          Files.copy(in, originalCopy);
        }
        ResumeContentModel model =
            objectMapper.treeToValue(content.getContent(), ResumeContentModel.class);
        TemplateMetadata metadata =
            objectMapper.treeToValue(template.getTemplate(), TemplateMetadata.class);
        GeneratedArtifact artifact =
            patchService.patch(originalCopy, type, model, metadata, output);
        byte[] bytes = Files.readAllBytes(output);
        StoredFile stored = storageService.store(
            resumeId + "/version-" + nextVersion, type.name().toLowerCase(),
            new ByteArrayInputStream(bytes));
        fileRepository.save(new ResumeFile(resume, ResumeFileKind.GENERATED,
            stored.storageKey(), artifact.mimeType(), stored.sizeBytes(), stored.sha256Hex()));
        var version = versionService.createGeneratedVersion(resume, content.getId(),
            template.getId(), stored.storageKey(), stored.sha256Hex());
        log.info("Generated version {} for resume {}", version.getVersionNumber(), resumeId);
        return new GenerateResponse(resumeId, version.getVersionNumber(), stored.storageKey(),
            stored.sha256Hex(), artifact.mimeType());
      } finally {
        Files.deleteIfExists(originalCopy);
        Files.deleteIfExists(output);
        Files.deleteIfExists(workDir);
      }
    } catch (ResumeNotFoundException | ResumeConflictException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Generation failed for resume " + resumeId, e);
    }
  }
}

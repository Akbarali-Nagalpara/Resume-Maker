package com.resumeflow.resume.service;

import com.resumeflow.exception.InvalidResumeContentException;
import com.resumeflow.exception.ResumeNotFoundException;
import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.resume.dto.ContentResponse;
import com.resumeflow.resume.dto.EditorStateResponse;
import com.resumeflow.resume.dto.GenerateResponse;
import com.resumeflow.resume.dto.PreviewResponse;
import com.resumeflow.resume.dto.UploadResponse;
import com.resumeflow.resume.dto.VersionResponse;
import com.resumeflow.resume.entity.Resume;
import com.resumeflow.resume.entity.ResumeFile;
import com.resumeflow.resume.entity.ResumeFileKind;
import com.resumeflow.resume.entity.ResumeVersion;
import com.resumeflow.resume.mapper.ResumeMapper;
import com.resumeflow.resume.repository.ResumeContentRepository;
import com.resumeflow.resume.repository.ResumeFileRepository;
import com.resumeflow.resume.repository.ResumeRepository;
import com.resumeflow.resume.repository.ResumeSectionRepository;
import com.resumeflow.resume.repository.ResumeTemplateRepository;
import com.resumeflow.resume.repository.ResumeVersionRepository;
import com.resumeflow.resume.storage.FileStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates the resume lifecycle for controllers. Business rules live in
 * the domain services; this class wires them into whole operations.
 */
@Service
public class ResumeService {

  private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

  private final ResumeRepository resumeRepository;
  private final ResumeFileRepository fileRepository;
  private final ResumeSectionRepository sectionRepository;
  private final ResumeVersionRepository versionRepository;
  private final ResumeContentRepository contentRepository;
  private final ResumeTemplateRepository templateRepository;
  private final ResumeUploadService uploadService;
  private final ResumeContentService contentService;
  private final ResumeTemplateService templateService;
  private final ResumeVersionService versionService;
  private final ResumeGenerationService generationService;
  private final ResumePreviewService previewService;
  private final FileStorageService storageService;
  private final ResumeCacheService cacheService;
  private final ResumePatchService patchService;

  public ResumeService(
      ResumeRepository resumeRepository,
      ResumeFileRepository fileRepository,
      ResumeSectionRepository sectionRepository,
      ResumeVersionRepository versionRepository,
      ResumeContentRepository contentRepository,
      ResumeTemplateRepository templateRepository,
      ResumeCacheService cacheService,
      ResumePatchService patchService,
      ResumeUploadService uploadService,
      ResumeContentService contentService,
      ResumeTemplateService templateService,
      ResumeVersionService versionService,
      ResumeGenerationService generationService,
      ResumePreviewService previewService,
      FileStorageService storageService) {
    this.resumeRepository = resumeRepository;
    this.fileRepository = fileRepository;
    this.sectionRepository = sectionRepository;
    this.versionRepository = versionRepository;
    this.contentRepository = contentRepository;
    this.templateRepository = templateRepository;
    this.uploadService = uploadService;
    this.contentService = contentService;
    this.templateService = templateService;
    this.versionService = versionService;
    this.generationService = generationService;
    this.previewService = previewService;
    this.storageService = storageService;
    this.cacheService = cacheService;
    this.patchService = patchService;
  }

  public UploadResponse upload(MultipartFile file) {
    return uploadService.upload(file);
  }

  @Transactional(readOnly = true)
  public EditorStateResponse editorState(UUID resumeId) {
    return cacheService
        .getEditor(resumeId)
        .orElseGet(
            () -> {
              Resume resume = require(resumeId);
              var content = contentService.latestContent(resumeId);
              var templateNode = templateService.getTemplateNode(resumeId);
              EditorStateResponse state =
                  new EditorStateResponse(
                      ResumeMapper.toResumeResponse(resume),
                      content.getContent(),
                      templateNode,
                      sectionDtos(resumeId),
                      content.getVersionNumber(),
                      content.getId());
              cacheService.putEditor(resumeId, state);
              return state;
            });
  }

  public ContentResponse updateContent(
      UUID resumeId, Long expectedVersion, tools.jackson.databind.JsonNode content) {
    ContentResponse response = contentService.updateContent(resumeId, expectedVersion, content);
    cacheService.evictResume(resumeId);
    return response;
  }

  public GenerateResponse generate(UUID resumeId) {
    GenerateResponse response = generationService.generate(resumeId);
    cacheService.evictResume(resumeId);
    return response;
  }

  public PreviewResponse preview(UUID resumeId) {
    require(resumeId);
    return cacheService
        .getPreview(resumeId)
        .orElseGet(
            () -> {
              PreviewResponse response = previewService.preview(resumeId);
              cacheService.putPreview(resumeId, response);
              return response;
            });
  }

  @Transactional(readOnly = true)
  public List<VersionResponse> versions(UUID resumeId) {
    require(resumeId);
    return versionService.list(resumeId).stream().map(ResumeMapper::toVersionResponse).toList();
  }

  @Transactional(readOnly = true)
  public VersionResponse version(UUID resumeId, int versionNumber) {
    require(resumeId);
    ResumeVersion version = versionService.list(resumeId).stream()
        .filter(v -> v.getVersionNumber() == versionNumber)
        .findFirst()
        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
    return ResumeMapper.toVersionResponse(version);
  }

  /** Loads the latest generated artifact, generating on demand when absent. */
  public DownloadedFile download(UUID resumeId) {
    return download(resumeId, "source");
  }

  /**
   * Downloads the latest generated artifact. {@code format} is {@code source}
   * (same format as the upload, best fidelity) or {@code pdf} (converted via
   * a real renderer when a conversion engine is available).
   */
  public DownloadedFile download(UUID resumeId, String format) {
    if (!format.equals("source") && !format.equals("pdf")) {
      throw new InvalidResumeContentException(
          "Unsupported download format '" + format + "' (use 'source' or 'pdf').");
    }
    Resume resume = require(resumeId);
    PreviewResponse preview = previewService.currentPreview(resumeId);
    if (preview == null) {
      var generated = generationService.generate(resumeId);
      preview = new PreviewResponse(resumeId, generated.versionNumber(), generated.storageKey(),
          generated.mimeType());
    }
    if (format.equals("pdf") && !preview.mimeType().contains("pdf")) {
      return downloadAsPdf(resume, preview);
    }
    try {
      Resource resource = storageService.load(preview.storageKey());
      String filename = downloadFilename(resume, preview.mimeType());
      return new DownloadedFile(resource, filename, preview.mimeType());
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load generated artifact for " + resumeId, e);
    }
  }

  private DownloadedFile downloadAsPdf(Resume resume, PreviewResponse preview) {
    try {
      Resource generated = storageService.load(preview.storageKey());
      java.nio.file.Path workDir =
          java.nio.file.Files.createTempDirectory("resumeflow-pdf-");
      java.nio.file.Path source = workDir.resolve("generated.docx");
      try (var in = generated.getInputStream()) {
        java.nio.file.Files.copy(in, source);
      }
      var pdf = patchService
          .convertDocxToPdf(source, workDir)
          .orElseThrow(() -> new ResumeParseException(
              "PDF conversion is unavailable in this environment (no conversion engine). "
                  + "Download the source-format file instead.",
              null));
      byte[] bytes = java.nio.file.Files.readAllBytes(pdf);
      org.springframework.core.io.ByteArrayResource resource =
          new org.springframework.core.io.ByteArrayResource(bytes);
      String filename = downloadFilename(resume, "application/pdf");
      return new DownloadedFile(resource, filename, "application/pdf");
    } catch (ResumeParseException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Cannot convert generated artifact to PDF for " + resume.getId(), e);
    }
  }

  @Transactional
  public void delete(UUID resumeId) {
    Resume resume = require(resumeId);
    List<ResumeFile> files = new ArrayList<>(
        fileRepository.findByResumeIdAndKindOrderByCreatedAtDesc(resumeId,
            ResumeFileKind.ORIGINAL));
    files.addAll(fileRepository.findByResumeIdAndKindOrderByCreatedAtDesc(resumeId,
        ResumeFileKind.GENERATED));
    files.addAll(fileRepository.findByResumeIdAndKindOrderByCreatedAtDesc(resumeId,
        ResumeFileKind.PREVIEW));
    for (ResumeFile file : files) {
      try {
        storageService.delete(file.getStorageKey());
      } catch (Exception e) {
        log.warn("Could not delete artifact {}", file.getStorageKey(), e);
      }
    }
    fileRepository.deleteAll(files);
    versionRepository.deleteAll(versionRepository.findByResumeIdOrderByVersionNumberAsc(resumeId));
    contentRepository.deleteAll(contentRepository.findByResumeId(resumeId));
    templateRepository.findByResumeId(resumeId).ifPresent(templateRepository::delete);
    sectionRepository.deleteAll(sectionRepository.findByResumeIdOrderByPositionAsc(resumeId));
    resumeRepository.delete(resume);
    cacheService.evictResume(resumeId);
  }

  public record DownloadedFile(Resource resource, String filename, String mimeType) {
  }

  private Resume require(UUID resumeId) {
    return resumeRepository.findById(resumeId)
        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
  }

  private List<com.resumeflow.resume.dto.SectionDto> sectionDtos(UUID resumeId) {
    return sectionRepository.findByResumeIdOrderByPositionAsc(resumeId).stream()
        .map(ResumeMapper::toSectionDto)
        .toList();
  }

  private static String downloadFilename(Resume resume, String mimeType) {
    String base = resume.getOriginalFilename();
    int dot = base.lastIndexOf('.');
    if (dot > 0) {
      base = base.substring(0, dot);
    }
    String extension = mimeType.contains("pdf") ? ".pdf" : ".docx";
    return base + "-updated" + extension;
  }
}

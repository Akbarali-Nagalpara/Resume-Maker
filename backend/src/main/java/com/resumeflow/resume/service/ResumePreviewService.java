package com.resumeflow.resume.service;

import com.resumeflow.resume.dto.PreviewResponse;
import com.resumeflow.resume.entity.ResumeFileKind;
import com.resumeflow.resume.entity.ResumeVersion;
import com.resumeflow.resume.repository.ResumeFileRepository;
import com.resumeflow.resume.repository.ResumeVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the current preview: the latest generated artifact when present,
 * generating on demand otherwise. Preview metadata (not binaries) is what
 * gets cached in Redis.
 */
@Service
public class ResumePreviewService {

  private final ResumeVersionRepository versionRepository;
  private final ResumeFileRepository fileRepository;
  private final ResumeVersionService versionService;
  private final ResumeGenerationService generationService;

  public ResumePreviewService(
      ResumeVersionRepository versionRepository,
      ResumeFileRepository fileRepository,
      ResumeVersionService versionService,
      ResumeGenerationService generationService) {
    this.versionRepository = versionRepository;
    this.fileRepository = fileRepository;
    this.versionService = versionService;
    this.generationService = generationService;
  }

  @Transactional
  public PreviewResponse preview(UUID resumeId) {
    Optional<ResumeVersion> latest =
        versionRepository.findFirstByResumeIdOrderByVersionNumberDesc(resumeId);
    if (latest.isPresent() && latest.get().getGeneratedStorageKey() != null) {
      ResumeVersion version = latest.get();
      return new PreviewResponse(resumeId, version.getVersionNumber(),
          version.getGeneratedStorageKey(), mimeFor(resumeId));
    }
    var generated = generationService.generate(resumeId);
    return new PreviewResponse(resumeId, generated.versionNumber(), generated.storageKey(),
        generated.mimeType());
  }

  @Transactional(readOnly = true)
  public PreviewResponse currentPreview(UUID resumeId) {
    ResumeVersion version = versionService.latest(resumeId);
    if (version.getGeneratedStorageKey() == null) {
      return null;
    }
    return new PreviewResponse(resumeId, version.getVersionNumber(),
        version.getGeneratedStorageKey(), mimeFor(resumeId));
  }

  private String mimeFor(UUID resumeId) {
    return fileRepository
        .findFirstByResumeIdAndKindOrderByCreatedAtDesc(resumeId, ResumeFileKind.GENERATED)
        .map(com.resumeflow.resume.entity.ResumeFile::getMimeType)
        .orElse("application/octet-stream");
  }
}

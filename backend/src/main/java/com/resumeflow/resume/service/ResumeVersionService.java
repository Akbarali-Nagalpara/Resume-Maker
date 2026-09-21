package com.resumeflow.resume.service;

import com.resumeflow.resume.entity.Resume;
import com.resumeflow.resume.entity.ResumeVersion;
import com.resumeflow.resume.repository.ResumeVersionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns immutable version history: append-only, never updated or deleted. */
@Service
public class ResumeVersionService {

  private final ResumeVersionRepository versionRepository;

  public ResumeVersionService(ResumeVersionRepository versionRepository) {
    this.versionRepository = versionRepository;
  }

  @Transactional
  public ResumeVersion createInitial(Resume resume, UUID contentId, UUID templateId) {
    return save(resume, 1, contentId, templateId, null, null);
  }

  @Transactional
  public ResumeVersion createContentVersion(Resume resume, UUID contentId, UUID templateId) {
    return save(resume, nextVersionNumber(resume.getId()), contentId, templateId, null, null);
  }

  @Transactional
  public ResumeVersion createGeneratedVersion(
      Resume resume, UUID contentId, UUID templateId, String storageKey, String checksum) {
    return save(
        resume, nextVersionNumber(resume.getId()), contentId, templateId, storageKey, checksum);
  }

  @Transactional(readOnly = true)
  public List<ResumeVersion> list(UUID resumeId) {
    return versionRepository.findByResumeIdOrderByVersionNumberAsc(resumeId);
  }

  @Transactional(readOnly = true)
  public int nextVersionNumber(UUID resumeId) {
    return versionRepository
        .findFirstByResumeIdOrderByVersionNumberDesc(resumeId)
        .map(version -> version.getVersionNumber() + 1)
        .orElse(1);
  }

  @Transactional(readOnly = true)
  public ResumeVersion latest(UUID resumeId) {
    return versionRepository
        .findFirstByResumeIdOrderByVersionNumberDesc(resumeId)
        .orElseThrow(() -> new IllegalStateException("No versions for resume " + resumeId));
  }

  private ResumeVersion save(
      Resume resume,
      int versionNumber,
      UUID contentId,
      UUID templateId,
      String storageKey,
      String checksum) {
    return versionRepository.save(
        new ResumeVersion(resume, versionNumber, contentId, templateId, storageKey, checksum));
  }
}

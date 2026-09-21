package com.resumeflow.resume.mapper;

import com.resumeflow.resume.dto.ResumeResponse;
import com.resumeflow.resume.dto.SectionDto;
import com.resumeflow.resume.dto.VersionResponse;
import com.resumeflow.resume.entity.Resume;
import com.resumeflow.resume.entity.ResumeSection;
import com.resumeflow.resume.entity.ResumeVersion;

/** Maps entities to DTOs. No business logic lives here. */
public final class ResumeMapper {

  private ResumeMapper() {
  }

  public static ResumeResponse toResumeResponse(Resume resume) {
    return new ResumeResponse(
        resume.getId(),
        resume.getOriginalFilename(),
        resume.getMimeType(),
        resume.getChecksum(),
        resume.getStatus().name(),
        resume.getVersion() == null ? 0 : resume.getVersion(),
        resume.getCreatedAt(),
        resume.getUpdatedAt());
  }

  public static SectionDto toSectionDto(ResumeSection section) {
    return new SectionDto(section.getSectionKey(), section.getTitle(), section.getPosition());
  }

  public static VersionResponse toVersionResponse(ResumeVersion version) {
    return new VersionResponse(
        version.getVersionNumber(),
        version.getContentSnapshotId(),
        version.getTemplateSnapshotId(),
        version.getGeneratedStorageKey(),
        version.getCreatedAt());
  }
}

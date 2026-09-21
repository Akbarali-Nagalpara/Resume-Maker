package com.resumeflow.resume.repository;

import com.resumeflow.resume.entity.ResumeContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeContentRepository extends JpaRepository<ResumeContent, UUID> {

  Optional<ResumeContent> findFirstByResumeIdOrderByVersionNumberDesc(UUID resumeId);

  Optional<ResumeContent> findByResumeIdAndVersionNumber(UUID resumeId, int versionNumber);

  java.util.List<ResumeContent> findByResumeId(UUID resumeId);
}

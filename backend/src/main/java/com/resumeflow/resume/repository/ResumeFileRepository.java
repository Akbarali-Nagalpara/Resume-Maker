package com.resumeflow.resume.repository;

import com.resumeflow.resume.entity.ResumeFile;
import com.resumeflow.resume.entity.ResumeFileKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeFileRepository extends JpaRepository<ResumeFile, UUID> {

  List<ResumeFile> findByResumeIdAndKindOrderByCreatedAtDesc(UUID resumeId, ResumeFileKind kind);

  Optional<ResumeFile> findFirstByResumeIdAndKindOrderByCreatedAtDesc(
      UUID resumeId, ResumeFileKind kind);
}

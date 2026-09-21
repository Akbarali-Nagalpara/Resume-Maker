package com.resumeflow.resume.repository;

import com.resumeflow.resume.entity.ResumeVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {

  List<ResumeVersion> findByResumeIdOrderByVersionNumberAsc(UUID resumeId);

  Optional<ResumeVersion> findFirstByResumeIdOrderByVersionNumberDesc(UUID resumeId);

  Optional<ResumeVersion> findByResumeIdAndVersionNumber(UUID resumeId, int versionNumber);
}

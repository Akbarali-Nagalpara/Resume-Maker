package com.resumeflow.resume.repository;

import com.resumeflow.resume.entity.ResumeTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeTemplateRepository extends JpaRepository<ResumeTemplate, UUID> {

  Optional<ResumeTemplate> findByResumeId(UUID resumeId);
}

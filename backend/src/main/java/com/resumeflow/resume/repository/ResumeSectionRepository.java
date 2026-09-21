package com.resumeflow.resume.repository;

import com.resumeflow.resume.entity.ResumeSection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeSectionRepository extends JpaRepository<ResumeSection, UUID> {

  List<ResumeSection> findByResumeIdOrderByPositionAsc(UUID resumeId);

  void deleteByResumeId(UUID resumeId);
}

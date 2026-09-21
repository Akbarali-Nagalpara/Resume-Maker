package com.resumeflow.resume.service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.resumeflow.exception.InvalidResumeContentException;
import com.resumeflow.exception.ResumeConflictException;
import com.resumeflow.exception.ResumeNotFoundException;
import com.resumeflow.resume.dto.ContentResponse;
import com.resumeflow.resume.entity.Resume;
import com.resumeflow.resume.entity.ResumeContent;
import com.resumeflow.resume.entity.ResumeSection;
import com.resumeflow.resume.entity.ResumeStatus;
import com.resumeflow.resume.parser.ParsedResume;
import com.resumeflow.resume.parser.ResumeContentModel;
import com.resumeflow.resume.repository.ResumeContentRepository;
import com.resumeflow.resume.repository.ResumeRepository;
import com.resumeflow.resume.repository.ResumeSectionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates structured resume content only. Template rows are never touched
 * here, so content edits cannot accidentally modify the preserved design.
 */
@Service
public class ResumeContentService {

  private final ResumeRepository resumeRepository;
  private final ResumeContentRepository contentRepository;
  private final ResumeSectionRepository sectionRepository;
  private final ResumeVersionService versionService;
  private final ResumeTemplateService templateService;
  private final ObjectMapper objectMapper;

  public ResumeContentService(
      ResumeRepository resumeRepository,
      ResumeContentRepository contentRepository,
      ResumeSectionRepository sectionRepository,
      ResumeVersionService versionService,
      ResumeTemplateService templateService,
      ObjectMapper objectMapper) {
    this.resumeRepository = resumeRepository;
    this.contentRepository = contentRepository;
    this.sectionRepository = sectionRepository;
    this.versionService = versionService;
    this.templateService = templateService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public ResumeContent latestContent(UUID resumeId) {
    requireReady(resumeId);
    return contentRepository
        .findFirstByResumeIdOrderByVersionNumberDesc(resumeId)
        .orElseThrow(() -> new IllegalStateException("No content for resume " + resumeId));
  }

  @Transactional
  public ContentResponse updateContent(UUID resumeId, Long expectedVersion, JsonNode content) {
    Resume resume = requireReady(resumeId);
    if (!resume.getVersion().equals(expectedVersion)) {
      throw new ResumeConflictException(
          "Resume was modified concurrently. Reload and retry. Expected version "
              + expectedVersion + " but found " + resume.getVersion() + ".");
    }
    ResumeContentModel model = validate(content);
    JsonNode normalized = objectMapper.valueToTree(model);

    ResumeContent snapshot = contentRepository.findFirstByResumeIdOrderByVersionNumberDesc(resumeId)
        .orElseThrow(() -> new IllegalStateException("No content for resume " + resumeId));
    int nextVersion = snapshot.getVersionNumber() + 1;
    ResumeContent saved = contentRepository.save(new ResumeContent(resume, nextVersion, normalized));
    rebuildSections(resume, model);
    versionService.createContentVersion(
        resume, saved.getId(), templateService.getTemplate(resumeId).getId());
    int touched = resumeRepository.compareAndTouch(resumeId, expectedVersion, Instant.now());
    if (touched == 0) {
      throw new ResumeConflictException("Resume was modified concurrently. Reload and retry.");
    }
    Resume reloaded = resumeRepository.findById(resumeId)
        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
    return new ContentResponse(resumeId, nextVersion, normalized, reloaded.getUpdatedAt());
  }

  /** Validates and normalizes inbound editor content. Unknown fields are ignored. */
  public ResumeContentModel validate(JsonNode content) {
    if (content == null || content.isNull()) {
      throw new InvalidResumeContentException("Content must be a JSON object.");
    }
    final ResumeContentModel model;
    try {
      model = objectMapper
          .readerFor(ResumeContentModel.class)
          .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .readValue(content);
    } catch (Exception e) {
      throw new InvalidResumeContentException("Content has an invalid shape: " + e.getMessage());
    }
    if (model.personal() == null || model.personal().name() == null
        || model.personal().name().isBlank()) {
      throw new InvalidResumeContentException("personal.name is required.");
    }
    return new ResumeContentModel(
        model.personal(),
        model.summary(),
        orEmpty(model.skills()),
        orEmpty(model.experience()),
        orEmpty(model.projects()),
        orEmpty(model.education()),
        orEmpty(model.additionalSections()));
  }

  private void rebuildSections(Resume resume, ResumeContentModel model) {
    sectionRepository.deleteAll(
        sectionRepository.findByResumeIdOrderByPositionAsc(resume.getId()));
    sectionRepository.flush();
    List<ResumeSection> sections = new ArrayList<>();
    int position = 0;
    sections.add(new ResumeSection(resume, "personal", "Personal Information", position++));
    if (model.summary() != null && !model.summary().isBlank()) {
      sections.add(new ResumeSection(resume, "summary", "Summary", position++));
    }
    if (!model.skills().isEmpty()) {
      sections.add(new ResumeSection(resume, "skills", "Skills", position++));
    }
    if (!model.experience().isEmpty()) {
      sections.add(new ResumeSection(resume, "experience", "Experience", position++));
    }
    if (!model.projects().isEmpty()) {
      sections.add(new ResumeSection(resume, "projects", "Projects", position++));
    }
    if (!model.education().isEmpty()) {
      sections.add(new ResumeSection(resume, "education", "Education", position++));
    }
    for (var additional : model.additionalSections()) {
      sections.add(new ResumeSection(resume, additional.key(), additional.title(), position++));
    }
    sectionRepository.saveAll(sections);
  }

  /** Seeds the section index from freshly parsed content. */
  @Transactional
  public void seedSections(Resume resume, ParsedResume parsed) {
    sectionRepository.deleteAll(
        sectionRepository.findByResumeIdOrderByPositionAsc(resume.getId()));
    sectionRepository.flush();
    List<ResumeSection> sections = new ArrayList<>();
    sections.add(new ResumeSection(resume, "personal", "Personal Information", 0));
    for (ParsedResume.SectionIndexEntry entry : parsed.sections()) {
      sections.add(new ResumeSection(resume, entry.key(), entry.title(), entry.position() + 1));
    }
    sectionRepository.saveAll(sections);
  }

  private Resume requireReady(UUID resumeId) {
    Resume resume =
        resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
    if (resume.getStatus() != ResumeStatus.READY) {
      throw new InvalidResumeContentException(
          "Resume is not editable yet (status: " + resume.getStatus() + ").");
    }
    return resume;
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : List.copyOf(list);
  }
}
